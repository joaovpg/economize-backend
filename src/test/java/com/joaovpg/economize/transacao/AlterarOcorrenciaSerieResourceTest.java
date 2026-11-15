package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SupressaoRecorrenciaRepository;
import com.joaovpg.economize.transacao.domain.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
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
  private static final LocalDate OUTUBRO_DIA_10 = LocalDate.of(2026, 10, 10);
  private static final String MENSAL_DIA_10 =
      repeticao("MENSAL", "DIA_DO_MES", "{\"tipo\":\"INDEFINIDO\"}");
  private static final String MENSAL_DIA_UTIL =
      repeticao("MENSAL", "DIA_UTIL", "{\"tipo\":\"INDEFINIDO\"}");
  private static final String PARCELAS_TRES =
      repeticao(
          "MENSAL",
          "DIA_DO_MES",
          "{\"tipo\":\"PARCELAS\",\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}");

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;
  @Inject SegmentoRecorrenciaRepository segmentoRepository;
  @Inject SupressaoRecorrenciaRepository supressaoRepository;
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
  void efetivarOcorrenciaVirtualCriaRegistroVinculadoSemSupressao() {
    var token = autenticar();
    var segmentoId = criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");

    var efetivada =
        alterar(
                token,
                chave,
                alteracao(
                    (String) outubro.get("versaoEdicao"),
                    SOMENTE_ESTA,
                    MENSAL_DIA_10,
                    "EFETIVADA",
                    "2026-10-10",
                    "100"))
            .statusCode(200)
            .body("origem", equalTo("TRANSACAO_RECORRENTE"))
            .body("virtual", equalTo(false))
            .body("protegida", equalTo(true))
            .body("situacao", equalTo("EFETIVADA"))
            .body("efetivadoEm", notNullValue())
            .body("chaveEdicao", equalTo(chave))
            .body("serie.segmentoId", equalTo(segmentoId))
            .body("serie.dataOriginal", equalTo("2026-10-10"))
            .body("repeticao.frequencia", equalTo("MENSAL"))
            .body("escoposDisponiveis", hasSize(2));
    String operacaoId = efetivada.extract().path("operacaoId");

    assertEquals(1, contarVinculadas(segmentoId, OUTUBRO_DIA_10));
    assertEquals(0, contarSupressoes(segmentoId));
    consultar(token, "2026-10", "2026-11")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", hasSize(1))
        .body("itens.find { it.dataFinanceira == '2026-10-10' }.operacaoId", equalTo(operacaoId))
        .body(
            "itens.find { it.dataFinanceira == '2026-11-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"));
  }

  @Test
  void efetivarOcorrenciaMantemChaveItemEPosicaoNaConsulta() {
    var token = autenticar();
    criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    List<String> antes =
        consultar(token, "2026-10", "2026-10").statusCode(200).extract().path("itens.chaveItem");
    assertEquals(2, antes.size());
    assertNotNull(antes.get(0));
    assertNotEquals(antes.get(0), antes.get(1));
    Map<String, Object> primeira =
        consultar(token, "2026-10", "2026-10").extract().path("itens[0]");
    assertEquals(primeira.get("chaveEdicao"), primeira.get("chaveItem"));

    alterar(
            token,
            (String) primeira.get("chaveEdicao"),
            alteracao(
                (String) primeira.get("versaoEdicao"),
                SOMENTE_ESTA,
                MENSAL_DIA_10,
                "EFETIVADA",
                "2026-10-10",
                "100"))
        .statusCode(200)
        .body("chaveItem", equalTo(antes.get(0)));

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens[0].virtual", equalTo(false))
        .body("itens.chaveItem", equalTo(antes));
  }

  @Test
  void alterarValorCriaRegistroVinculadoEUmaNovaEdicaoAlteraOMesmo() {
    var token = autenticar();
    var segmentoId = criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");

    var primeira =
        alterar(
                token,
                chave,
                alteracao(
                    (String) outubro.get("versaoEdicao"),
                    SOMENTE_ESTA,
                    MENSAL_DIA_10,
                    "PLANEJADA",
                    "2026-10-10",
                    "80"))
            .statusCode(200)
            .body("origem", equalTo("TRANSACAO_RECORRENTE"))
            .body("descricao", equalTo("Mercado"))
            .body("valor", equalTo(new BigDecimal("-80.0000")));
    String operacaoId = primeira.extract().path("operacaoId");
    String versaoPrimeira = primeira.extract().path("versaoEdicao");

    alterar(
            token,
            chave,
            alteracao(versaoPrimeira, SOMENTE_ESTA, MENSAL_DIA_10, "PLANEJADA", "2026-10-10", "90"))
        .statusCode(200)
        .body("operacaoId", equalTo(operacaoId))
        .body("valor", equalTo(new BigDecimal("-90.0000")));

    assertEquals(1, contarVinculadas(segmentoId, OUTUBRO_DIA_10));
    assertEquals(0, contarSupressoes(segmentoId));
  }

  @Test
  void alterarDataFinanceiraNaoMudaIdentidadeNemDuplicaAOcorrencia() {
    var token = autenticar();
    var segmentoId = criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");

    var movida =
        alterar(
                token,
                chave,
                alteracao(
                    (String) outubro.get("versaoEdicao"),
                    SOMENTE_ESTA,
                    MENSAL_DIA_10,
                    "PLANEJADA",
                    "2026-10-12",
                    "80"))
            .statusCode(200)
            .body("chaveEdicao", equalTo(chave))
            .body("dataFinanceira", equalTo("2026-10-12"))
            .body("serie.dataOriginal", equalTo("2026-10-10"));
    String operacaoId = movida.extract().path("operacaoId");

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", empty())
        .body("itens.findAll { it.dataFinanceira == '2026-10-12' }", hasSize(1));

    alterar(
            token,
            chave,
            alteracao(
                (String) movida.extract().path("versaoEdicao"),
                SOMENTE_ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-12",
                "70"))
        .statusCode(200)
        .body("operacaoId", equalTo(operacaoId));

    assertEquals(1, contarVinculadas(segmentoId, OUTUBRO_DIA_10));
    consultar(token, "2026-10", "2026-10")
        .body("itens.findAll { it.dataFinanceira == '2026-10-12' }", hasSize(1));
  }

  @Test
  void replanejarOcorrenciaEfetivadaLimpaEfetivadoEmEMantemVinculoENumeracao() {
    var token = autenticar();
    criarSerie(token, "EFETIVADA", INICIO, PARCELAS_TRES);
    var parcela = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) parcela.get("chaveEdicao");

    var efetivada =
        alterar(
                token,
                chave,
                alteracao(
                    (String) parcela.get("versaoEdicao"),
                    SOMENTE_ESTA,
                    PARCELAS_TRES,
                    "EFETIVADA",
                    "2026-10-10",
                    "100"))
            .statusCode(200)
            .body("origem", equalTo("PARCELA"))
            .body("efetivadoEm", notNullValue())
            .body("serie.numeroParcela", equalTo(2))
            .body("serie.totalParcelas", equalTo(3));
    String operacaoId = efetivada.extract().path("operacaoId");
    String efetivadoEm = efetivada.extract().path("efetivadoEm");

    alterar(
            token,
            chave,
            alteracao(
                (String) efetivada.extract().path("versaoEdicao"),
                SOMENTE_ESTA,
                PARCELAS_TRES,
                "PLANEJADA",
                "2026-10-10",
                "100"))
        .statusCode(200)
        .body("situacao", equalTo("PLANEJADA"))
        .body("efetivadoEm", nullValue())
        .body("operacaoId", equalTo(operacaoId))
        .body("origem", equalTo("PARCELA"))
        .body("serie.numeroParcela", equalTo(2))
        .body("serie.totalParcelas", equalTo(3));

    assertNotNull(efetivadoEm);
    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", hasSize(1))
        .body("itens.find { it.dataFinanceira == '2026-10-10' }.serie.numeroParcela", equalTo(2));
  }

  @Test
  void repeticaoDiferenteDaLidaRespondeEscopoInvalidoSemGravarNada() {
    var token = autenticar();
    var segmentoId = criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");

    for (var repeticaoDivergente :
        new String[] {
          "null",
          repeticao("MENSAL", "DIA_DO_MES", "{\"tipo\":\"EM_DATA\",\"data\":\"2027-01-10\"}"),
          "{\"frequencia\":\"MENSAL\",\"intervalo\":2,\"ancoragem\":\"DIA_DO_MES\","
              + "\"termino\":{\"tipo\":\"INDEFINIDO\"}}"
        }) {
      alterar(
              token,
              chave,
              alteracao(versao, SOMENTE_ESTA, repeticaoDivergente, "PLANEJADA", "2026-10-12", "80"))
          .statusCode(422)
          .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    }

    assertEquals(0, contarVinculadas(segmentoId, OUTUBRO_DIA_10));
    assertEquals(0, contarSupressoes(segmentoId));
    var depois = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    assertEquals(versao, depois.get("versaoEdicao"));
    assertEquals("TRANSACAO_RECORRENTE", depois.get("origem"));
  }

  @Test
  void aceitaRepeticaoLidaSemNumeroDiaUtilEInformaOQueFoiCalculado() {
    var token = autenticar();
    var segmentoId = criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_UTIL);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-12");
    var repeticaoLida = (Map<?, ?>) outubro.get("repeticao");
    assertEquals(8, ((Number) repeticaoLida.get("numeroDiaUtil")).intValue());

    var editada =
        alterar(
                token,
                (String) outubro.get("chaveEdicao"),
                alteracao(
                    (String) outubro.get("versaoEdicao"),
                    SOMENTE_ESTA,
                    MENSAL_DIA_UTIL,
                    "PLANEJADA",
                    "2026-10-12",
                    "70"))
            .statusCode(200)
            .body("repeticao.ancoragem", equalTo("DIA_UTIL"))
            .body("repeticao.numeroDiaUtil", equalTo(8))
            .body("serie.segmentoId", equalTo(segmentoId));
    assertEquals(1, contarVinculadas(segmentoId, LocalDate.of(2026, 10, 12)));
    assertNotNull(editada.extract().path("operacaoId"));
  }

  @Test
  void ocorrenciaFisicaSobreviveAEsterAsProximasIniciadoAntesDela() {
    var token = autenticar();
    criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var fisica =
        alterar(
                token,
                (String) outubro.get("chaveEdicao"),
                alteracao(
                    (String) outubro.get("versaoEdicao"),
                    SOMENTE_ESTA,
                    MENSAL_DIA_10,
                    "PLANEJADA",
                    "2026-10-12",
                    "80"))
            .statusCode(200);
    String operacaoId = fisica.extract().path("operacaoId");

    var setembro = itemDe(token, "2026-09", "2026-09", INICIO);
    alterar(
            token,
            (String) setembro.get("chaveEdicao"),
            estaEAsProximas((String) setembro.get("versaoEdicao"), MENSAL_DIA_10, INICIO, "200"))
        .statusCode(200);

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-12' }", hasSize(1))
        .body("itens.find { it.dataFinanceira == '2026-10-12' }.operacaoId", equalTo(operacaoId))
        .body(
            "itens.find { it.dataFinanceira == '2026-10-12' }.valor",
            equalTo(new BigDecimal("-80.0000")))
        .body("itens.find { it.dataFinanceira == '2026-10-12' }.protegida", equalTo(true))
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.valor",
            equalTo(new BigDecimal("-200.0000")));
  }

  @Test
  void preservaInstanteDeOcorrenciaEfetivadaAoAlterarOValor() {
    var token = autenticar();
    criarSerie(token, "EFETIVADA", INICIO, MENSAL_DIA_10);
    var antes = itemDe(token, "2026-09", "2026-09", INICIO);
    var efetivadoEm = (String) antes.get("efetivadoEm");
    assertNotNull(efetivadoEm);

    alterar(
            token,
            (String) antes.get("chaveEdicao"),
            alteracao(
                (String) antes.get("versaoEdicao"),
                SOMENTE_ESTA,
                MENSAL_DIA_10,
                "EFETIVADA",
                INICIO,
                "120"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("situacao", equalTo("EFETIVADA"))
        .body("efetivadoEm", equalTo(efetivadoEm))
        .body("protegida", equalTo(true))
        .body("valor", equalTo(new BigDecimal("-120.0000")));

    consultar(token, "2026-09", "2026-09")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-09-10' }", hasSize(1));
  }

  @Test
  void alteraOcorrenciaJaMaterializadaNoProprioRegistro() {
    var token = autenticar();
    var segmentoId = UUID.fromString(criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10));
    var operacaoOriginal =
        QuarkusTransaction.requiringNew()
            .call(
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
                  excecao.setIdentificadorRecorrencia(OUTUBRO_DIA_10);
                  excecao.setExcecaoRecorrencia(true);
                  transacaoRepository.persist(excecao);
                  return excecao.getId().toString();
                });

    var individualizada = itemDe(token, "2026-10", "2026-10", "2026-10-15");
    assertEquals("TRANSACAO_RECORRENTE", individualizada.get("origem"));
    assertEquals(operacaoOriginal, individualizada.get("operacaoId"));

    alterar(
            token,
            (String) individualizada.get("chaveEdicao"),
            alteracao(
                (String) individualizada.get("versaoEdicao"),
                SOMENTE_ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-16",
                "60"))
        .statusCode(200)
        .body("operacaoId", equalTo(operacaoOriginal))
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
  void rejeitaVersaoObsoletaSemGravarEAtualizaRevisaoDoGrupo() {
    var token = autenticar();
    criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var setembro = itemDe(token, "2026-09", "2026-09", INICIO);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) setembro.get("chaveEdicao"),
            alteracao(
                (String) setembro.get("versaoEdicao"),
                SOMENTE_ESTA,
                MENSAL_DIA_10,
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
                MENSAL_DIA_10,
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
    var segmentoId = criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");

    alterar(
            tokenOutro,
            chave,
            alteracao(versao, SOMENTE_ESTA, MENSAL_DIA_10, "PLANEJADA", "2026-10-12", "80"))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    alterar(
            token,
            IdentidadeEdicaoTransacao.chaveRecorrencia(UUID.randomUUID(), OUTUBRO_DIA_10),
            alteracao(versao, SOMENTE_ESTA, MENSAL_DIA_10, "PLANEJADA", "2026-10-12", "80"))
        .statusCode(404);
    alterar(
            token,
            IdentidadeEdicaoTransacao.chaveRecorrencia(
                UUID.fromString(segmentoId), LocalDate.of(2026, 10, 11)),
            alteracao(versao, SOMENTE_ESTA, MENSAL_DIA_10, "PLANEJADA", "2026-10-12", "80"))
        .statusCode(404);

    consultar(token, "2026-10", "2026-10")
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"));
  }

  @Test
  void exigeEscopoSomenteEstaParaOcorrenciaDeSerie() {
    var token = autenticar();
    criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");

    for (var escopo :
        new String[] {SEM_ESCOPO, "\"escopo\":null,", "\"escopo\":\"THIS_AND_FUTURE\","}) {
      alterar(
              token,
              chave,
              alteracao(versao, escopo, MENSAL_DIA_10, "PLANEJADA", "2026-10-12", "80"))
          .statusCode(422)
          .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    }

    consultar(token, "2026-10", "2026-10")
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"));
  }

  @Test
  void falhaDeValidacaoNaoGravaRegistroVinculado() {
    var token = autenticar();
    var segmentoId = criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");

    alterar(
            token,
            chave,
            alteracao(versao, SOMENTE_ESTA, MENSAL_DIA_10, "PLANEJADA", "2025-12-31", "80"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL"));
    alterar(
            token,
            chave,
            alteracao(versao, SOMENTE_ESTA, MENSAL_DIA_10, "EFETIVADA", "2026-12-01", "80"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_FUTURA"));

    assertEquals(0, contarVinculadas(segmentoId, OUTUBRO_DIA_10));
    assertEquals(0, contarSupressoes(segmentoId));
    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"))
        .body("itens.findAll { it.dataFinanceira == '2026-12-01' }", empty());
  }

  @Test
  void preservaNumeracaoDaParcelaAoAlterarVinculada() {
    var token = autenticar();
    criarSerie(token, "PLANEJADA", INICIO, PARCELAS_TRES);
    var parcela = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) parcela.get("chaveEdicao"),
            alteracao(
                (String) parcela.get("versaoEdicao"),
                SOMENTE_ESTA,
                PARCELAS_TRES,
                "PLANEJADA",
                "2026-10-10",
                "100"))
        .statusCode(200)
        .body("origem", equalTo("PARCELA"))
        .body("serie.numeroParcela", equalTo(2))
        .body("serie.totalParcelas", equalTo(3));

    consultar(token, "2026-09", "2026-11")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", hasSize(1))
        .body("itens.find { it.dataFinanceira == '2026-10-10' }.origem", equalTo("PARCELA"))
        .body("itens.find { it.dataFinanceira == '2026-11-10' }.serie.numeroParcela", equalTo(3))
        .body("itens.find { it.dataFinanceira == '2026-11-10' }.origem", equalTo("PARCELA"));
  }

  @Test
  void excluirOcorrenciaVinculadaRemoveRegistroESuprimeAOriginal() {
    var token = autenticar();
    criarSerie(token, "PLANEJADA", INICIO, MENSAL_DIA_10);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    Map<String, Object> vinculada =
        alterar(
                token,
                (String) outubro.get("chaveEdicao"),
                alteracao(
                    (String) outubro.get("versaoEdicao"),
                    SOMENTE_ESTA,
                    MENSAL_DIA_10,
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
        .queryParam("escopo", "SOMENTE_ESTA")
        .queryParam("versaoEdicao", vinculada.get("versaoEdicao"))
        .when()
        .delete("/api/transacoes/{chaveEdicao}", vinculada.get("chaveEdicao"))
        .then()
        .statusCode(204);

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-10-10' }", empty())
        .body("itens.findAll { it.dataFinanceira == '2026-10-12' }", empty());
  }

  private String criarSerie(String token, String situacao, String data, String repeticao) {
    return criar(token, corpo(situacao, data, repeticao))
        .statusCode(201)
        .extract()
        .path("serie.segmentoId");
  }

  private long contarVinculadas(String segmentoId, LocalDate identificador) {
    var id = UUID.fromString(segmentoId);
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                transacaoRepository.count(
                    "segmentoRecorrencia.id = ?1 and identificadorRecorrencia = ?2"
                        + " and excecaoRecorrencia = true",
                    id,
                    identificador));
  }

  private long contarSupressoes(String segmentoId) {
    var id = UUID.fromString(segmentoId);
    return QuarkusTransaction.requiringNew()
        .call(() -> supressaoRepository.count("segmento.id = ?1", id));
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

  private String estaEAsProximas(
      String versao, String repeticao, String dataFinanceira, String valor) {
    return """
    {"escopo":"ESTA_E_AS_PROXIMAS","repeticao":%s,"contaId":"%s","categoriaId":null,
     "situacao":"PLANEJADA","tipo":"DESPESA","descricao":"Mercado","observacoes":null,
     "valor":%s,"dataFinanceira":"%s","versaoEdicao":"%s",
     "politicaSobreposicao":"MANTER_SOMENTE_PRESERVADA","resolucoesSobreposicao":[]}
    """
        .formatted(repeticao, contaId, valor, dataFinanceira, versao);
  }

  private static String repeticao(String frequencia, String ancoragem, String termino) {
    return """
    {"frequencia":"%s","intervalo":1,"ancoragem":"%s","termino":%s}
    """
        .formatted(frequencia, ancoragem, termino);
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
