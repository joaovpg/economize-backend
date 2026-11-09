package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2Factory;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PreverAlteracaoTransacaoResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");
  private static final String ESTA_E_AS_PROXIMAS = "\"escopo\":\"ESTA_E_AS_PROXIMAS\",";
  private static final String SOMENTE_ESTA = "\"escopo\":\"SOMENTE_ESTA\",";
  private static final String SEM_ESCOPO = "";
  private static final String INICIO = "2026-09-10";
  // Acima do limite de corpo configurado em application.yaml (quarkus.http.limits.max-body-size).
  private static final int TAMANHO_CORPO_ACIMA_DO_LIMITE = 70_000;
  private static final String MENSAL_DIA_10 =
      repeticao("MENSAL", "[]", "[10]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
  private static final String MENSAL_DIA_12 =
      repeticao("MENSAL", "[]", "[12]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
  private static final String MENSAL_DIA_5 =
      repeticao("MENSAL", "[]", "[5]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;
  @Inject SegmentoRecorrenciaRepository segmentoRepository;
  @Inject TransacaoRepository transacaoRepository;

  private String email;
  private String emailOutroUsuario;
  private UUID contaId;

  @BeforeEach
  void prepararDados() {
    email = "previa-" + UUID.randomUUID() + "@example.com";
    emailOutroUsuario = "previa-outra-" + UUID.randomUUID() + "@example.com";
    var argon2 = Argon2Factory.create();
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              var usuario = new Usuario();
              usuario.setNome("Pessoa de teste");
              usuario.setEmail(email);
              usuario.setSenhaHash(argon2.hash(2, 19_456, 1, "senha-segura".toCharArray()));
              usuario.setTimezone(FUSO.getId());
              usuario.setAtivo(true);
              usuarioRepository.persist(usuario);

              var conta = new ContaFinanceira();
              conta.setUsuario(usuario);
              conta.setNome("Conta principal");
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
            });
  }

  @Test
  void previaDeSerieSemSobreposicaoDevolveCandidatasSemGravarNada() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var antes = consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString();

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2026-12",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(200)
        .body("versaoEdicao", equalTo(outubro.get("versaoEdicao")))
        .body("periodo.inicio", equalTo("2026-10"))
        .body("periodo.fim", equalTo("2026-12"))
        .body("politicaSobreposicao", equalTo("MANTER_SOMENTE_PRESERVADA"))
        .body("itensPreservados", empty())
        .body("ocorrenciasNovas", hasSize(3))
        .body("ocorrenciasNovas[0].dataOriginal", equalTo("2026-10-12"))
        .body("ocorrenciasNovas[0].dataFinanceira", equalTo("2026-10-12"))
        .body("ocorrenciasNovas[0].valor", equalTo(new BigDecimal("-80.0000")))
        .body("ocorrenciasNovas[0].numeroParcela", nullValue())
        .body("ocorrenciasNovas[2].dataFinanceira", equalTo("2026-12-12"))
        .body("sobreposicoes", empty())
        .body("quantidadeSobreposicoesForaPeriodo", equalTo(0));

    var depois = consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString();
    assertEquals(antes, depois);
  }

  @Test
  void previaInformaSobreposicaoComOcorrenciaProtegidaDeslocadaSemAlterarSuaIdentidade() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var segmentoId = UUID.fromString(criada.extract().path("serie.segmentoId"));
    persistirExcecao(segmentoId, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12));
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chaveDezembro =
        (String) itemDe(token, "2026-12", "2026-12", "2026-12-12").get("chaveEdicao");

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2026-12",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(200)
        .body("itensPreservados", hasSize(1))
        .body("itensPreservados[0].chaveEdicao", equalTo(chaveDezembro))
        .body("itensPreservados[0].dataFinanceira", equalTo("2026-12-12"))
        .body("itensPreservados[0].serie.dataOriginal", equalTo("2026-12-10"))
        .body("itensPreservados[0].protegida", equalTo(true))
        .body("sobreposicoes", hasSize(1))
        .body("sobreposicoes[0].dataOriginalCandidata", equalTo("2026-12-12"))
        .body("sobreposicoes[0].dataFinanceira", equalTo("2026-12-12"))
        .body("sobreposicoes[0].chavesPreservadas", contains(chaveDezembro))
        .body("sobreposicoes[0].resolucaoSugerida", equalTo("MANTER_SOMENTE_PRESERVADA"))
        .body(
            "sobreposicoes[0].resolucoesPermitidas",
            contains("MANTER_SOMENTE_PRESERVADA", "MANTER_AMBAS"))
        .body("quantidadeSobreposicoesForaPeriodo", equalTo(0));
  }

  @Test
  void selecionadaProtegidaNaoConcorreConsigoMesma() {
    var token = autenticar();
    criar(token, corpo("EFETIVADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var setembro = itemDe(token, "2026-09", "2026-09", INICIO);

    previa(
            token,
            (String) setembro.get("chaveEdicao"),
            previaCorpo(
                (String) setembro.get("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_10,
                "EFETIVADA",
                INICIO,
                "100",
                "2026-09",
                "2026-10",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(200)
        .body("itensPreservados", empty())
        .body("ocorrenciasNovas", hasSize(2))
        .body("sobreposicoes", empty())
        .body("quantidadeSobreposicoesForaPeriodo", equalTo(0));
  }

  @Test
  void agrupaPreservadasDoMesmoDiaComChavesEmOrdemLexicografica() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var segmentoId = UUID.fromString(criada.extract().path("serie.segmentoId"));
    persistirExcecao(segmentoId, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12));
    persistirExcecao(segmentoId, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12));
    persistirExcecao(segmentoId, LocalDate.of(2027, 1, 10), LocalDate.of(2026, 12, 12));
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chaveNovembro = chaveDaOcorrencia(token, "2026-11", "2026-11", "2026-11-10");
    var chaveDezembro = chaveDaOcorrencia(token, "2026-12", "2026-12", "2026-12-10");
    var chaveJaneiro = chaveDaOcorrencia(token, "2026-12", "2026-12", "2027-01-10");
    var dezembroOrdenado = List.of(chaveDezembro, chaveJaneiro).stream().sorted().toList();

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2026-12",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(200)
        .body("itensPreservados", hasSize(3))
        .body("itensPreservados.dataFinanceira", contains("2026-11-12", "2026-12-12", "2026-12-12"))
        .body("sobreposicoes", hasSize(2))
        .body("sobreposicoes[0].dataFinanceira", equalTo("2026-11-12"))
        .body("sobreposicoes[0].chavesPreservadas", contains(chaveNovembro))
        .body("sobreposicoes[1].dataFinanceira", equalTo("2026-12-12"))
        .body("sobreposicoes[1].chavesPreservadas", contains(dezembroOrdenado.toArray()));
  }

  @Test
  void contaColisoesForaDoPeriodoSemExpandirRepeticaoInfinita() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var segmentoId = UUID.fromString(criada.extract().path("serie.segmentoId"));
    persistirExcecao(segmentoId, LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12));
    persistirExcecao(segmentoId, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 3, 12));
    persistirExcecao(segmentoId, LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 15));
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2026-11",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(200)
        .body("ocorrenciasNovas", hasSize(2))
        .body("ocorrenciasNovas[1].dataFinanceira", equalTo("2026-11-12"))
        .body("itensPreservados", empty())
        .body("sobreposicoes", empty())
        .body("quantidadeSobreposicoesForaPeriodo", equalTo(2));
  }

  @Test
  void previaDeTransacaoSimplesConvertidaEmRepeticaoNaoTemSobreposicoes() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", "2026-10-05", "null")).statusCode(201);
    var chave = (String) criada.extract().path("chaveEdicao");
    var versao = (String) criada.extract().path("versaoEdicao");

    previa(
            token,
            chave,
            previaCorpo(
                versao,
                SEM_ESCOPO,
                MENSAL_DIA_5,
                "PLANEJADA",
                "2026-10-05",
                "100",
                "2026-10",
                "2026-11",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(200)
        .body("versaoEdicao", equalTo(versao))
        .body("itensPreservados", empty())
        .body("ocorrenciasNovas", hasSize(2))
        .body("ocorrenciasNovas[0].dataFinanceira", equalTo("2026-10-05"))
        .body("ocorrenciasNovas[1].dataFinanceira", equalTo("2026-11-05"))
        .body("sobreposicoes", empty())
        .body("quantidadeSobreposicoesForaPeriodo", equalTo(0));
  }

  @Test
  void versaoObsoletaNaPreviaRetornaConflito() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");
    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "PLANEJADA",
                "2026-10-12",
                "80"))
        .statusCode(200);

    previa(
            token,
            (String) novembro.get("chaveEdicao"),
            previaCorpo(
                (String) novembro.get("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-11-12",
                "80",
                "2026-11",
                "2026-11",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(409)
        .body("type", equalTo("urn:economize:problem:VERSAO_DESATUALIZADA"));
  }

  @Test
  void corpoDaPreviaAcimaDoLimiteRetornaDadosInvalidosSemGravar() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var antes = consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString();
    var corpo =
        previaCorpo(
            (String) outubro.get("versaoEdicao"),
            ESTA_E_AS_PROXIMAS,
            MENSAL_DIA_12,
            "PLANEJADA",
            "2026-10-12",
            "80",
            "2026-10",
            "2026-12",
            "MANTER_SOMENTE_PRESERVADA");
    var acimaDoLimite = "{" + " ".repeat(TAMANHO_CORPO_ACIMA_DO_LIMITE) + corpo.substring(1);

    previa(token, (String) outubro.get("chaveEdicao"), acimaDoLimite)
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));

    assertEquals(
        antes, consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString());
  }

  @Test
  void periodoForaDeUmaADozeMesesOuInvertidoRetornaDadosInvalidos() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2027-10",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-12",
                "2026-10",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));
  }

  @Test
  void campoDesconhecidoOuPoliticaAusenteOuNaoPermitidaRetornaDadosInvalidos() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var versao = (String) outubro.get("versaoEdicao");
    var chave = (String) outubro.get("chaveEdicao");

    var comResolucoes =
        previaCorpo(
                versao,
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2026-12",
                "MANTER_SOMENTE_PRESERVADA")
            .replace("\"alteracao\":{", "\"alteracao\":{\"resolucoesSobreposicao\":[],");
    previa(token, chave, comResolucoes)
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));

    var semPolitica =
        previaCorpo(
                versao,
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2026-12",
                null)
            .replaceAll("\"politicaSobreposicao\":null,?", "");
    previa(token, chave, semPolitica)
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));

    var manterAmbas =
        previaCorpo(
            versao,
            ESTA_E_AS_PROXIMAS,
            MENSAL_DIA_12,
            "PLANEJADA",
            "2026-10-12",
            "80",
            "2026-10",
            "2026-12",
            "MANTER_AMBAS");
    previa(token, chave, manterAmbas)
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));
  }

  @Test
  void escopoNaoAplicavelRetornaEscopoInvalido() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var simples = criar(token, corpo("PLANEJADA", "2026-10-05", "null")).statusCode(201);

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                SOMENTE_ESTA,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2026-12",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                SEM_ESCOPO,
                MENSAL_DIA_12,
                "PLANEJADA",
                "2026-10-12",
                "80",
                "2026-10",
                "2026-12",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));

    previa(
            token,
            (String) simples.extract().path("chaveEdicao"),
            previaCorpo(
                (String) simples.extract().path("versaoEdicao"),
                ESTA_E_AS_PROXIMAS,
                MENSAL_DIA_5,
                "PLANEJADA",
                "2026-10-05",
                "100",
                "2026-10",
                "2026-11",
                "MANTER_SOMENTE_PRESERVADA"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
  }

  @Test
  void ocorrenciaDeOutroUsuarioOuChaveInvalidaRetornaNaoEncontrada() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var corpo =
        previaCorpo(
            (String) outubro.get("versaoEdicao"),
            ESTA_E_AS_PROXIMAS,
            MENSAL_DIA_12,
            "PLANEJADA",
            "2026-10-12",
            "80",
            "2026-10",
            "2026-12",
            "MANTER_SOMENTE_PRESERVADA");

    previa(autenticarOutroUsuario(), (String) outubro.get("chaveEdicao"), corpo)
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    previa(token, "chave-inexistente", corpo)
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
  }

  private ValidatableResponse previa(String token, String chave, String corpo) {
    return given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(corpo)
        .when()
        .post("/api/transacoes/{chave}/previa-edicao", chave)
        .then();
  }

  private ValidatableResponse criar(String token, String corpo) {
    return given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
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
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .queryParam("inicio", inicio)
        .queryParam("fim", fim)
        .when()
        .get("/api/transacoes")
        .then();
  }

  private ValidatableResponse alterar(String token, String chave, String corpo) {
    return given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
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

  private String chaveDaOcorrencia(String token, String inicio, String fim, String dataOriginal) {
    Map<String, Object> item =
        consultar(token, inicio, fim)
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("itens.find { it.serie?.dataOriginal == '" + dataOriginal + "' }");
    assertNotNull(item, "ocorrencia com data original " + dataOriginal);
    return (String) item.get("chaveEdicao");
  }

  private void persistirExcecao(
      UUID segmentoId, LocalDate identificador, LocalDate dataFinanceira) {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              var segmento = segmentoRepository.findById(segmentoId);
              var excecao = new Transacao();
              excecao.setUsuario(segmento.getUsuario());
              excecao.setConta(segmento.getConta());
              excecao.setGrupoRecorrencia(segmento.getGrupo());
              excecao.setSegmentoRecorrencia(segmento);
              excecao.setTipo(TipoTransacao.DESPESA);
              excecao.setSituacao(SituacaoTransacao.PLANEJADA);
              excecao.setDescricao("Individualizada");
              excecao.setValor(new BigDecimal("50.0000"));
              excecao.setDataFinanceira(dataFinanceira);
              excecao.setIdentificadorRecorrencia(identificador);
              excecao.setExcecaoRecorrencia(true);
              transacaoRepository.persist(excecao);
            });
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
    return """
    {%s"repeticao":%s,"contaId":"%s","categoriaId":null,"situacao":"%s",
     "tipo":"DESPESA","descricao":"Mercado","observacoes":null,"valor":%s,
     "dataFinanceira":"%s","versaoEdicao":"%s"}
    """
        .formatted(escopo, repeticao, contaId, situacao, valor, dataFinanceira, versao);
  }

  private String previaCorpo(
      String versao,
      String escopo,
      String repeticao,
      String situacao,
      String dataFinanceira,
      String valor,
      String inicio,
      String fim,
      String politica) {
    var alteracao = alteracao(versao, escopo, repeticao, situacao, dataFinanceira, valor);
    var politicaJson =
        politica == null ? "" : ",\"politicaSobreposicao\":\"%s\"".formatted(politica);
    return """
    {"alteracao":%s,"periodo":{"inicio":"%s","fim":"%s"}%s}
    """
        .formatted(alteracao.strip(), inicio, fim, politicaJson);
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
