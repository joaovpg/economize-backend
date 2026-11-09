package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2Factory;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contrato de exclusão unificado: chave de transação simples ou de ocorrência de série. */
@QuarkusTest
class ExcluirTransacaoResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");
  private static final String INICIO = "2026-09-10";
  private static final String MENSAL_DIA_10 =
      repeticao("MENSAL", "[]", "[10]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
  private static final String DIARIA_QUATRO =
      repeticao(
          "DIARIA",
          "[]",
          "[]",
          "PADRAO",
          "{\"tipo\":\"APOS_OCORRENCIAS\",\"quantidade\":4}",
          "null");
  private static final String DIARIA_INDEFINIDA =
      repeticao("DIARIA", "[]", "[]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
  private static final String PARCELAS_TRES =
      repeticao(
          "MENSAL",
          "[]",
          "[]",
          "AJUSTAR_ULTIMO_DIA_MES",
          "null",
          "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}");

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;

  private String email;
  private String emailOutroUsuario;
  private UUID contaId;

  @BeforeEach
  @Transactional
  void prepararDados() {
    email = "exclusao-" + UUID.randomUUID() + "@example.com";
    emailOutroUsuario = "exclusao-outra-" + UUID.randomUUID() + "@example.com";
    var argon2 = Argon2Factory.create();

    var usuario = new Usuario();
    usuario.setNome("Pessoa de exclusao");
    usuario.setEmail(email);
    usuario.setSenhaHash(argon2.hash(2, 19_456, 1, "senha-segura".toCharArray()));
    usuario.setTimezone(FUSO.getId());
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);

    var conta = new ContaFinanceira();
    conta.setUsuario(usuario);
    conta.setNome("Conta de exclusao");
    conta.setMoeda("BRL");
    conta.setSaldoInicial(BigDecimal.ZERO);
    conta.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
    contaRepository.persist(conta);
    contaRepository.flush();
    contaId = conta.getId();

    var outro = new Usuario();
    outro.setNome("Outra pessoa");
    outro.setEmail(emailOutroUsuario);
    outro.setSenhaHash(argon2.hash(2, 19_456, 1, "outra-senha".toCharArray()));
    outro.setTimezone(FUSO.getId());
    outro.setAtivo(true);
    usuarioRepository.persist(outro);
  }

  @Test
  void excluiTransacaoSimplesPelaChaveEDevolveNaoEncontradoNaRepeticao() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, "null")).statusCode(201);
    String chave = criada.extract().path("chaveEdicao");

    excluir(token, chave, null).statusCode(204);
    excluir(token, chave, null)
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    consultar(token, "2026-09", "2026-09").statusCode(200).body("itens", empty());
  }

  @Test
  void rejeitaEscopoEmTransacaoSimplesEIdentificadorAntigoDeUuid() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, "null")).statusCode(201);
    String chave = criada.extract().path("chaveEdicao");
    String id = criada.extract().path("operacaoId");

    excluir(token, chave, "SOMENTE_ESTA")
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    excluir(token, id, null)
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    consultar(token, "2026-09", "2026-09").statusCode(200).body("itens", hasSize(1));
  }

  @Test
  void exigeEscopoValidoParaOcorrenciaDeSerieSemAliasesEmIngles() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var chave = itemDe(token, "2026-10", "2026-10", "2026-10-10").get("chaveEdicao");

    excluir(token, (String) chave, null)
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    excluir(token, (String) chave, "ONLY_THIS")
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    excluir(token, (String) chave, "THIS_AND_FUTURE")
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    excluir(token, (String) chave, "FUTURO")
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    consultar(token, "2026-10", "2026-10").statusCode(200).body("itens", hasSize(1));
  }

  @Test
  void excluiSomenteEstaDeVirtualSemRenumerarParcelas() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2026-01-10", PARCELAS_TRES)).statusCode(201);
    var fevereiro = itemDe(token, "2026-02", "2026-02", "2026-02-10");

    excluir(token, (String) fevereiro.get("chaveEdicao"), "SOMENTE_ESTA").statusCode(204);

    consultar(token, "2026-01", "2026-03")
        .statusCode(200)
        .body("itens", hasSize(2))
        .body("itens[0].dataFinanceira", equalTo("2026-01-10"))
        .body("itens[1].dataFinanceira", equalTo("2026-03-10"))
        .body("itens[0].serie.numeroParcela", equalTo(1))
        .body("itens[1].serie.numeroParcela", equalTo(3))
        .body("itens[0].serie.totalParcelas", equalTo(3))
        .body("itens[1].serie.totalParcelas", equalTo(3));
    excluir(token, (String) fevereiro.get("chaveEdicao"), "SOMENTE_ESTA")
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
  }

  @Test
  void excluirSomenteEstaRevisaOGrupoEAtualizaVersaoDasOcorrenciasVizinhas() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");
    var versaoAntes = novembro.get("versaoEdicao");

    excluir(token, (String) outubro.get("chaveEdicao"), "SOMENTE_ESTA").statusCode(204);

    var novembroDepois = itemDe(token, "2026-11", "2026-11", "2026-11-10");
    assertNotEquals(versaoAntes, novembroDepois.get("versaoEdicao"));
    assertNotNull(novembroDepois.get("chaveEdicao"));
    consultar(token, "2026-10", "2026-10").statusCode(200).body("itens", empty());
  }

  @Test
  void excluirEstaEAsProximasNaoApagaEfetivadaPosteriorNemRemoveAsAnteriores() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                "SOMENTE_ESTA",
                "null",
                "EFETIVADA",
                "2026-11-08",
                "100"))
        .statusCode(200);

    excluir(token, (String) outubro.get("chaveEdicao"), "ESTA_E_AS_PROXIMAS").statusCode(204);

    consultar(token, "2026-09", "2026-12")
        .statusCode(200)
        .body("itens", hasSize(2))
        .body("itens[0].dataFinanceira", equalTo("2026-09-10"))
        .body("itens[1].dataFinanceira", equalTo("2026-11-08"))
        .body("itens[1].situacao", equalTo("EFETIVADA"));
  }

  @Test
  void excluirEstaEAsProximasDeOcorrenciaEfetivadaRemoveSelecionadaECancelaAsFuturas() {
    var token = autenticar();
    criar(token, corpo("EFETIVADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var primeira = itemDe(token, "2026-09", "2026-09", INICIO);
    var chave = (String) primeira.get("chaveEdicao");
    assertNotNull(primeira.get("efetivadoEm"));

    excluir(token, chave, "ESTA_E_AS_PROXIMAS").statusCode(204);

    consultar(token, "2026-09", "2026-12").statusCode(200).body("itens", empty());
    excluir(token, chave, "ESTA_E_AS_PROXIMAS")
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
  }

  @Test
  void excluirOcorrenciaEfetivadaSomenteEstaRemoveSomenteAElaEMantemAsProximas() {
    var token = autenticar();
    criar(token, corpo("EFETIVADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var primeira = itemDe(token, "2026-09", "2026-09", INICIO);

    excluir(token, (String) primeira.get("chaveEdicao"), "SOMENTE_ESTA").statusCode(204);

    consultar(token, "2026-09", "2026-09").statusCode(200).body("itens", empty());
    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens", hasSize(1))
        .body("itens[0].dataFinanceira", equalTo("2026-10-10"))
        .body("itens[0].origem", equalTo("TRANSACAO_RECORRENTE"));
  }

  @Test
  void excluirEstaEAsProximasEncerraSegmentosFuturosDoGrupo() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2026-01-10", DIARIA_QUATRO)).statusCode(201);

    var dia12 = itemDe(token, "2026-01", "2026-01", "2026-01-12");
    var intermediario =
        alterar(
                token,
                (String) dia12.get("chaveEdicao"),
                alteracao(
                    (String) dia12.get("versaoEdicao"),
                    "ESTA_E_AS_PROXIMAS",
                    DIARIA_INDEFINIDA,
                    "PLANEJADA",
                    "2026-01-12",
                    "25",
                    "MANTER_SOMENTE_PRESERVADA",
                    "[]"))
            .statusCode(200)
            .extract();
    var chaveIntermediario = intermediario.path("chaveEdicao");

    var ultimo = itemDe(token, "2026-01", "2026-01", "2026-01-13");
    alterar(
            token,
            (String) ultimo.get("chaveEdicao"),
            alteracao(
                (String) ultimo.get("versaoEdicao"),
                "ESTA_E_AS_PROXIMAS",
                DIARIA_INDEFINIDA,
                "PLANEJADA",
                "2026-01-13",
                "30",
                "MANTER_SOMENTE_PRESERVADA",
                "[]"))
        .statusCode(200);
    var efetivar = itemDe(token, "2026-01", "2026-01", "2026-01-13");
    alterar(
            token,
            (String) efetivar.get("chaveEdicao"),
            alteracao(
                (String) efetivar.get("versaoEdicao"),
                "SOMENTE_ESTA",
                "null",
                "EFETIVADA",
                "2026-01-13",
                "30"))
        .statusCode(200)
        .body("situacao", equalTo("EFETIVADA"));

    excluir(token, (String) chaveIntermediario, "ESTA_E_AS_PROXIMAS").statusCode(204);

    consultar(token, "2026-01", "2026-01")
        .statusCode(200)
        .body("itens", hasSize(3))
        .body("itens[0].dataFinanceira", equalTo("2026-01-10"))
        .body("itens[1].dataFinanceira", equalTo("2026-01-11"))
        .body("itens[2].dataFinanceira", equalTo("2026-01-13"))
        .body("itens[2].situacao", equalTo("EFETIVADA"));
  }

  @Test
  void ocultaSerieAlheiaEChavesMalformadasOuInexistentes() {
    var tokenDono = autenticar();
    criar(tokenDono, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var chave = (String) itemDe(tokenDono, "2026-10", "2026-10", "2026-10-10").get("chaveEdicao");
    var tokenOutro = autenticarOutroUsuario();

    excluir(tokenOutro, chave, "SOMENTE_ESTA")
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    excluir(tokenOutro, UUID.randomUUID().toString(), null)
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    excluir(tokenDono, "chave-invalida", "SOMENTE_ESTA")
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    excluir(tokenDono, chave, "SOMENTE_ESTA").statusCode(204);
    consultar(tokenDono, "2026-10", "2026-10").statusCode(200).body("itens", empty());
  }

  @Test
  void exigeAutenticacaoParaExcluir() {
    given().when().delete("/api/transacoes/{chave}", "chave-qualquer").then().statusCode(401);
  }

  private ValidatableResponse criar(String token, String corpo) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(corpo)
        .when()
        .post("/api/transacoes")
        .then();
  }

  private ValidatableResponse consultar(String token, String inicio, String fim) {
    return given()
        .auth()
        .oauth2(token)
        .queryParam("inicio", inicio)
        .queryParam("fim", fim)
        .when()
        .get("/api/transacoes")
        .then();
  }

  private ValidatableResponse excluir(String token, String chave, String escopo) {
    var requisicao = given().auth().oauth2(token);
    if (escopo != null) {
      requisicao = requisicao.queryParam("escopo", escopo);
    }
    return requisicao.when().delete("/api/transacoes/{chave}", chave).then();
  }

  private ValidatableResponse alterar(String token, String chave, String corpo) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(corpo)
        .when()
        .put("/api/transacoes/{chave}", chave)
        .then();
  }

  private Map<String, Object> itemDe(String token, String inicio, String fim, String data) {
    Map<String, Object> item =
        consultar(token, inicio, fim)
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("itens.find { it.dataFinanceira == '" + data + "' }");
    assertNotNull(item, "item de " + data);
    return item;
  }

  private String corpo(String situacao, String dataFinanceira, String repeticao) {
    return """
    {"tipo":"DESPESA","contaId":"%s","categoriaId":null,"descricao":"Aluguel",
     "observacoes":null,"valor":100.00,"dataFinanceira":"%s","situacao":"%s",
     "repeticao":%s}
    """
        .formatted(contaId, dataFinanceira, situacao, repeticao);
  }

  private String alteracao(
      String versao,
      String escopo,
      String repeticao,
      String situacao,
      String dataFinanceira,
      String valor) {
    return alteracao(versao, escopo, repeticao, situacao, dataFinanceira, valor, null, null);
  }

  private String alteracao(
      String versao,
      String escopo,
      String repeticao,
      String situacao,
      String dataFinanceira,
      String valor,
      String politica,
      String resolucoes) {
    var json = new StringBuilder("{");
    if (escopo != null) {
      json.append("\"escopo\":\"").append(escopo).append("\",");
    }
    json.append("\"repeticao\":")
        .append(repeticao)
        .append(",\"contaId\":\"")
        .append(contaId)
        .append("\",\"categoriaId\":null,\"situacao\":\"")
        .append(situacao)
        .append("\",\"tipo\":\"DESPESA\",\"descricao\":\"Mercado\",\"observacoes\":null,")
        .append("\"valor\":")
        .append(valor)
        .append(",\"dataFinanceira\":\"")
        .append(dataFinanceira)
        .append("\",\"versaoEdicao\":\"")
        .append(versao)
        .append("\"");
    if (politica != null) {
      json.append(",\"politicaSobreposicao\":\"").append(politica).append("\"");
    }
    if (resolucoes != null) {
      json.append(",\"resolucoesSobreposicao\":").append(resolucoes);
    }
    return json.append("}").toString();
  }

  private static String repeticao(
      String frequencia,
      String diasSemana,
      String diasMes,
      String politica,
      String termino,
      String parcelamento) {
    return """
    {"frequencia":"%s","intervalo":1,"diasSemana":%s,"diasMes":%s,
     "politicaData":"%s","termino":%s,"parcelamento":%s}
    """
        .formatted(frequencia, diasSemana, diasMes, politica, termino, parcelamento);
  }

  private String autenticar() {
    return login(email, "senha-segura");
  }

  private String autenticarOutroUsuario() {
    return login(emailOutroUsuario, "outra-senha");
  }

  private String login(String email, String senha) {
    return given()
        .contentType("application/json")
        .body("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha))
        .when()
        .post("/api/autenticacao/login")
        .then()
        .statusCode(200)
        .extract()
        .response()
        .getCookie("economize_token");
  }
}
