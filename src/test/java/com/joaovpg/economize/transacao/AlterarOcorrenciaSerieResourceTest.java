package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
class AlterarOcorrenciaSerieResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");
  private static final String SOMENTE_ESTA = "\"escopo\":\"SOMENTE_ESTA\",";
  private static final String SEM_ESCOPO = "";
  private static final String INICIO = "2026-09-10";
  private static final String MENSAL_DIA_10 =
      repeticao("MENSAL", "[]", "[10]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
  private static final String MENSAL_DIA_20 =
      repeticao("MENSAL", "[]", "[20]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
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
  @Inject SegmentoRecorrenciaRepository segmentoRepository;
  @Inject TransacaoRepository transacaoRepository;

  private String email;
  private String emailOutroUsuario;
  private UUID contaId;

  @BeforeEach
  void prepararDados() {
    email = "ocorrencia-" + UUID.randomUUID() + "@example.com";
    emailOutroUsuario = "ocorrencia-outra-" + UUID.randomUUID() + "@example.com";
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
  void individualizaOcorrenciaVirtualComoSimplesSemReaparecerAOriginal() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var segmentoId = criada.extract().path("serie.segmentoId");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");

    alterar(token, chave, alteracao(versao, SOMENTE_ESTA, "null", "PLANEJADA", "2026-10-12", "80"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_SIMPLES"))
        .body("repeticao", nullValue())
        .body("escoposDisponiveis", empty())
        .body("protegida", equalTo(true))
        .body("virtual", equalTo(false))
        .body("dataFinanceira", equalTo("2026-10-12"))
        .body("valor", equalTo(new BigDecimal("-80.0000")))
        .body("serie.segmentoId", equalTo(segmentoId))
        .body("serie.dataOriginal", equalTo("2026-10-10"))
        .body("serie.numeroParcela", nullValue())
        .body("chaveEdicao", notNullValue())
        .body("versaoEdicao", notNullValue());

    consultar(token, "2026-10", "2026-11")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", empty())
        .body(
            "itens.find { it.dataFinanceira == '2026-10-12' }.origem", equalTo("TRANSACAO_SIMPLES"))
        .body(
            "itens.find { it.dataFinanceira == '2026-11-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"));
    consultar(token, "2026-11", "2026-11")
        .statusCode(200)
        .body("saldoAbertura", equalTo(new BigDecimal("-180.0000")));

    alterar(token, chave, alteracao(versao, SOMENTE_ESTA, "null", "PLANEJADA", "2026-10-12", "80"))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
  }

  @Test
  void preservaInstanteDeOcorrenciaEfetivadaAoIndividualizarComoSimples() {
    var token = autenticar();
    criar(token, corpo("EFETIVADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var antes = itemDe(token, "2026-09", "2026-09", INICIO);
    var efetivadoEm = (String) antes.get("efetivadoEm");
    assertNotNull(efetivadoEm);

    alterar(
            token,
            (String) antes.get("chaveEdicao"),
            alteracao(
                (String) antes.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "EFETIVADA",
                INICIO,
                "120"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_SIMPLES"))
        .body("situacao", equalTo("EFETIVADA"))
        .body("efetivadoEm", equalTo(efetivadoEm))
        .body("protegida", equalTo(true))
        .body("valor", equalTo(new BigDecimal("-120.0000")));

    consultar(token, "2026-09", "2026-09")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-09-10' }", hasSize(1));
  }

  @Test
  void individualizaOcorrenciaJaMaterializadaSemDuplicarMovimento() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var segmentoId = UUID.fromString(criada.extract().path("serie.segmentoId"));
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
              excecao.setDataFinanceira(LocalDate.of(2026, 10, 15));
              excecao.setIdentificadorRecorrencia(LocalDate.of(2026, 10, 10));
              excecao.setExcecaoRecorrencia(true);
              transacaoRepository.persist(excecao);
            });

    var individualizada = itemDe(token, "2026-10", "2026-10", "2026-10-15");
    assertEquals("TRANSACAO_RECORRENTE", individualizada.get("origem"));
    assertEquals(Boolean.TRUE, individualizada.get("protegida"));

    alterar(
            token,
            (String) individualizada.get("chaveEdicao"),
            alteracao(
                (String) individualizada.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "PLANEJADA",
                "2026-10-16",
                "60"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_SIMPLES"))
        .body("escoposDisponiveis", empty())
        .body("serie.dataOriginal", equalTo("2026-10-10"))
        .body("valor", equalTo(new BigDecimal("-60.0000")));

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-15' }", empty())
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", empty())
        .body("itens.findAll { it.dataFinanceira == '2026-10-16' }", hasSize(1));
    consultar(token, "2026-11", "2026-11")
        .body("saldoAbertura", equalTo(new BigDecimal("-160.0000")));
  }

  @Test
  void iniciaNovaSerieAPartirDaOcorrenciaSemAlterarAOrigem() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var segmentoOrigem = criada.extract().path("serie.segmentoId");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                SOMENTE_ESTA,
                MENSAL_DIA_20,
                "PLANEJADA",
                "2026-10-20",
                "100"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("repeticao.frequencia", equalTo("MENSAL"))
        .body("repeticao.diasMes", equalTo(List.of(20)))
        .body("escoposDisponiveis", hasSize(2))
        .body("virtual", equalTo(true))
        .body("protegida", equalTo(false))
        .body("serie.dataOriginal", equalTo("2026-10-20"));

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", empty())
        .body(
            "itens.find { it.dataFinanceira == '2026-10-20' }.serie.segmentoId",
            not(equalTo(segmentoOrigem)));
    consultar(token, "2026-11", "2026-11")
        .statusCode(200)
        .body(
            "itens.find { it.dataFinanceira == '2026-11-10' }.serie.segmentoId",
            equalTo(segmentoOrigem))
        .body("saldoAbertura", equalTo(new BigDecimal("-200.0000")));
  }

  @Test
  void iniciaNovaSerieEfetivadaPreservandoInstanteDaOcorrencia() {
    var token = autenticar();
    criar(token, corpo("EFETIVADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var antes = itemDe(token, "2026-09", "2026-09", INICIO);

    alterar(
            token,
            (String) antes.get("chaveEdicao"),
            alteracao(
                (String) antes.get("versaoEdicao"),
                SOMENTE_ESTA,
                MENSAL_DIA_10,
                "EFETIVADA",
                INICIO,
                "100"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("situacao", equalTo("EFETIVADA"))
        .body("virtual", equalTo(false))
        .body("efetivadoEm", equalTo(antes.get("efetivadoEm")))
        .body("escoposDisponiveis", hasSize(2));

    consultar(token, "2026-09", "2026-09")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-09-10' }", hasSize(1));
  }

  @Test
  void rejeitaVersaoObsoletaSemGravarEAtualizaRevisaoDoGrupo() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var setembro = itemDe(token, "2026-09", "2026-09", INICIO);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) setembro.get("chaveEdicao"),
            alteracao(
                (String) setembro.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "PLANEJADA",
                INICIO,
                "100"))
        .statusCode(200);

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "PLANEJADA",
                "2026-10-10",
                "70"))
        .statusCode(409)
        .body("type", equalTo("urn:economize:problem:VERSAO_DESATUALIZADA"));

    var outubroAtual = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    assertEquals("TRANSACAO_RECORRENTE", outubroAtual.get("origem"));
    assertNotEquals(outubro.get("versaoEdicao"), outubroAtual.get("versaoEdicao"));
  }

  @Test
  void ocultaSerieAlheiaESegmentoOuDataInexistentes() {
    var token = autenticar();
    var tokenOutro = autenticarOutroUsuario();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    String segmentoId = criada.extract().path("serie.segmentoId");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");

    alterar(
            tokenOutro,
            chave,
            alteracao(versao, SOMENTE_ESTA, "null", "PLANEJADA", "2026-10-12", "80"))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    alterar(
            token,
            com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao.chaveRecorrencia(
                UUID.randomUUID(), LocalDate.of(2026, 10, 10)),
            alteracao(versao, SOMENTE_ESTA, "null", "PLANEJADA", "2026-10-12", "80"))
        .statusCode(404);
    alterar(
            token,
            com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao.chaveRecorrencia(
                UUID.fromString(segmentoId), LocalDate.of(2026, 10, 11)),
            alteracao(versao, SOMENTE_ESTA, "null", "PLANEJADA", "2026-10-12", "80"))
        .statusCode(404);

    consultar(token, "2026-10", "2026-10")
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"));
  }

  @Test
  void exigeEscopoSomenteEstaParaOcorrenciaDeSerie() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");

    for (var escopo :
        new String[] {SEM_ESCOPO, "\"escopo\":null,", "\"escopo\":\"THIS_AND_FUTURE\","}) {
      alterar(token, chave, alteracao(versao, escopo, "null", "PLANEJADA", "2026-10-12", "80"))
          .statusCode(422)
          .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    }

    consultar(token, "2026-10", "2026-10")
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"));
  }

  @Test
  void falhaDeValidacaoNaoGravaSupressaoNemNovaSerie() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");

    alterar(
            token,
            chave,
            alteracao(
                versao,
                SOMENTE_ESTA,
                repeticao("MENSAL", "[]", "[]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null"),
                "PLANEJADA",
                "2026-10-20",
                "100"))
        .statusCode(422);
    alterar(token, chave, alteracao(versao, SOMENTE_ESTA, "null", "PLANEJADA", "2025-12-31", "80"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL"));
    alterar(token, chave, alteracao(versao, SOMENTE_ESTA, "null", "EFETIVADA", "2026-12-01", "80"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_FUTURA"));

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"))
        .body("itens.findAll { it.dataFinanceira == '2026-10-20' }", empty());
  }

  @Test
  void preservaNumeracaoDaParcelaIndividualizada() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, PARCELAS_TRES)).statusCode(201);
    var parcela = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) parcela.get("chaveEdicao"),
            alteracao(
                (String) parcela.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "PLANEJADA",
                "2026-10-10",
                "100"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_SIMPLES"))
        .body("serie.numeroParcela", equalTo(2))
        .body("serie.totalParcelas", equalTo(3));

    consultar(token, "2026-09", "2026-11")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", hasSize(1))
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem", equalTo("TRANSACAO_SIMPLES"))
        .body("itens.find { it.dataFinanceira == '2026-11-10' }.serie.numeroParcela", equalTo(3))
        .body("itens.find { it.dataFinanceira == '2026-11-10' }.origem", equalTo("PARCELA"));
  }

  @Test
  void mantemSerieHistoricaESupressaoDuravelAoEditarDestacadaComoSimples() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    Map<String, Object> destacada =
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
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    alterar(
            token,
            (String) destacada.get("chaveEdicao"),
            alteracao(
                (String) destacada.get("versaoEdicao"),
                SEM_ESCOPO,
                "null",
                "PLANEJADA",
                "2026-10-12",
                "90.0000"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_SIMPLES"))
        .body("protegida", equalTo(true))
        .body("serie.dataOriginal", equalTo("2026-10-10"))
        .body("valor", equalTo(new BigDecimal("-90.0000")));

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "PLANEJADA",
                "2026-11-10",
                "100"))
        .statusCode(409)
        .body("type", equalTo("urn:economize:problem:VERSAO_DESATUALIZADA"));

    consultar(token, "2026-10", "2026-10")
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", empty())
        .body(
            "itens.find { it.dataFinanceira == '2026-10-12' }.origem",
            equalTo("TRANSACAO_SIMPLES"));
  }

  @Test
  void excluirDestacadaMantemOcorrenciaOriginalSuprimida() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    Map<String, Object> destacada =
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
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");

    given()
        .auth()
        .oauth2(token)
        .when()
        .delete("/api/transacoes/{id}", destacada.get("operacaoId"))
        .then()
        .statusCode(204);

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", empty())
        .body("itens.findAll { it.dataFinanceira == '2026-10-12' }", empty());
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
