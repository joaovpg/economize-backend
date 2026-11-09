package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.anyOf;
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
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contratos HTTP de PUT com escopo ESTA_E_AS_PROXIMAS: cortes para simples, repetição ou
 * parcelamento, preservação de protegidas, resoluções de sobreposição e conversões.
 */
@QuarkusTest
class AlterarEstaEAsProximasResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");
  private static final String ESTA = "ESTA_E_AS_PROXIMAS";
  private static final String SOMENTE_ESTA = "SOMENTE_ESTA";
  private static final String INICIO = "2026-09-10";
  // Acima do limite de corpo configurado em application.yaml (quarkus.http.limits.max-body-size).
  private static final int TAMANHO_CORPO_ACIMA_DO_LIMITE = 70_000;
  private static final String MANTER_PRESERVADA = "MANTER_SOMENTE_PRESERVADA";
  private static final String MANTER_AMBAS = "MANTER_AMBAS";
  private static final String SEM_ESCOPO = null;
  private static final String MENSAL_DIA_10 =
      repeticao("MENSAL", "[]", "[10]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
  private static final String MENSAL_DIA_5 =
      repeticao("MENSAL", "[]", "[5]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
  private static final String MENSAL_DIA_15 =
      repeticao("MENSAL", "[]", "[15]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
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
  @Inject GrupoRecorrenciaRepository grupoRepository;
  @Inject SegmentoRecorrenciaRepository segmentoRepository;

  private String email;
  private UUID usuarioId;
  private UUID contaId;

  @BeforeEach
  void prepararDados() {
    email = "estas-proximas-" + UUID.randomUUID() + "@example.com";
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
              usuarioId = usuario.getId();

              var conta = new ContaFinanceira();
              conta.setUsuario(usuario);
              conta.setNome("Conta principal");
              conta.setMoeda("BRL");
              conta.setSaldoInicial(BigDecimal.ZERO);
              conta.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
              contaRepository.persist(conta);
              contaRepository.flush();
              contaId = conta.getId();
            });
  }

  @Test
  void cortaSerieParaSimplesAPartirDaOcorrenciaSelecionada() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var segmentoId = criada.extract().path("serie.segmentoId");
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                ESTA,
                "null",
                "PLANEJADA",
                "2026-11-12",
                "80",
                null,
                null))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_SIMPLES"))
        .body("repeticao", nullValue())
        .body("escoposDisponiveis", empty())
        .body("protegida", equalTo(true))
        .body("dataFinanceira", equalTo("2026-11-12"))
        .body("valor", equalTo(new BigDecimal("-80.0000")))
        .body("serie.segmentoId", equalTo(segmentoId))
        .body("serie.dataOriginal", equalTo("2026-11-10"));

    consultar(token, "2026-09", "2026-12")
        .statusCode(200)
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem",
            equalTo("TRANSACAO_RECORRENTE"))
        .body("itens.findAll { it.dataFinanceira == '2026-11-10' }", empty())
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' }", empty())
        .body(
            "itens.find { it.dataFinanceira == '2026-11-12' }.origem",
            equalTo("TRANSACAO_SIMPLES"));
    consultar(token, "2026-12", "2026-12")
        .statusCode(200)
        .body("saldoAbertura", equalTo(new BigDecimal("-280.0000")));
  }

  @Test
  void preservaIndividualizadaPosteriorAoCorte() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-12-10", "2026-12-10", "90");
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                ESTA,
                "null",
                "PLANEJADA",
                "2026-11-10",
                "80",
                null,
                null))
        .statusCode(200);

    consultar(token, "2026-12", "2026-12")
        .statusCode(200)
        .body(
            "itens.find { it.dataFinanceira == '2026-12-10' }.valor",
            equalTo(new BigDecimal("-90.0000")))
        .body("itens.find { it.dataFinanceira == '2026-12-10' }.protegida", equalTo(true))
        .body(
            "itens.find { it.dataFinanceira == '2026-12-10' }.origem",
            equalTo("TRANSACAO_SIMPLES"));
  }

  @Test
  void preservaEfetivadaPosteriorAoCorte() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var segmentoId = criada.extract().path("serie.segmentoId");
    efetivarOcorrencia(token, segmentoId, "2026-10-10")
        .statusCode(anyOf(equalTo(200), equalTo(201)));
    var setembro = itemDe(token, "2026-09", "2026-09", INICIO);

    alterar(
            token,
            (String) setembro.get("chaveEdicao"),
            alteracao(
                (String) setembro.get("versaoEdicao"),
                ESTA,
                "null",
                "PLANEJADA",
                INICIO,
                "60",
                null,
                null))
        .statusCode(200);

    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.origem", equalTo("TRANSACAO_SIMPLES"))
        .body("itens.find { it.dataFinanceira == '2026-10-10' }.situacao", equalTo("EFETIVADA"))
        .body(
            "itens.find { it.dataFinanceira == '2026-10-10' }.valor",
            equalTo(new BigDecimal("-100.0000")))
        .body("itens.find { it.dataFinanceira == '2026-10-10' }.protegida", equalTo(true));
  }

  @Test
  void novaRecorrenciaComMesmoTipoMantemGrupoECortaProjecaoAntiga() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var grupoId = criada.extract().path("serie.grupoId");
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_15,
                "PLANEJADA",
                "2026-11-15",
                "100",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("serie.grupoId", equalTo(grupoId))
        .body("serie.dataOriginal", equalTo("2026-11-15"))
        .body("repeticao.diasMes", hasSize(1));

    consultar(token, "2026-10", "2026-12")
        .statusCode(200)
        .body("itens.find { it.dataFinanceira == '2026-10-10' }", notNullValue())
        .body("itens.findAll { it.dataFinanceira == '2026-11-10' }", empty())
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' }", empty())
        .body("itens.find { it.dataFinanceira == '2026-11-15' }", notNullValue())
        .body("itens.find { it.dataFinanceira == '2026-12-15' }", notNullValue());
  }

  @Test
  void exigePoliticaEEscolhasSomenteAoCriarNovoTrecho() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_15,
                "PLANEJADA",
                "2026-11-15",
                "100",
                null,
                "[]"))
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));
    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_15,
                "PLANEJADA",
                "2026-11-15",
                "100",
                MANTER_PRESERVADA,
                null))
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));
  }

  @Test
  void rejeitaEscolhasDeSobreposicaoEmDestinoSimples() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                ESTA,
                "null",
                "PLANEJADA",
                "2026-11-10",
                "80",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:RESOLUCAO_SOBREPOSICAO_INVALIDA"));
  }

  @Test
  void politicaGeralSuprimeCandidataColidindoComDataFinanceiraDeslocada() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-11-10", "2026-12-10", "70");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200);

    consultar(token, "2026-12", "2026-12")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' }", hasSize(1))
        .body(
            "itens.find { it.dataFinanceira == '2026-12-10' }.valor",
            equalTo(new BigDecimal("-70.0000")))
        .body("itens.find { it.dataFinanceira == '2026-12-10' }.protegida", equalTo(true));
    consultar(token, "2026-11", "2026-11")
        .statusCode(200)
        .body(
            "itens.find { it.dataFinanceira == '2026-11-10' }.valor",
            equalTo(new BigDecimal("-120.0000")));
  }

  @Test
  void escolhaEspecificaPrevaleceSobrePoliticaGeral() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-11-10", "2026-12-10", "70");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_AMBAS,
                escolhas(escolha("2026-12-10", MANTER_PRESERVADA))))
        .statusCode(200);

    consultar(token, "2026-12", "2026-12")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' }", hasSize(1))
        .body(
            "itens.find { it.dataFinanceira == '2026-12-10' }.valor",
            equalTo(new BigDecimal("-70.0000")));
  }

  @Test
  void manterAmbasDuasImpactosNaMesmaData() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-11-10", "2026-12-10", "70");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_AMBAS,
                escolhas(escolha("2026-12-10", MANTER_AMBAS))))
        .statusCode(200);

    consultar(token, "2026-12", "2026-12")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' }", hasSize(2));
  }

  @Test
  void escolhasInvalidasRespondemSemAlterarNada() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-11-10", "2026-12-10", "70");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var versao = (String) outubro.get("versaoEdicao");
    var chave = (String) outubro.get("chaveEdicao");

    alterar(
            token,
            chave,
            alteracao(
                versao,
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                escolhas(escolha("2026-12-10", MANTER_AMBAS), escolha("2026-12-10", MANTER_AMBAS))))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:RESOLUCAO_SOBREPOSICAO_INVALIDA"));
    alterar(
            token,
            chave,
            alteracao(
                versao,
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                escolhas(escolha("2026-12-10", "FOO"))))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:RESOLUCAO_SOBREPOSICAO_INVALIDA"));
    alterar(
            token,
            chave,
            alteracao(
                versao,
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                escolhas(escolha("2026-11-10", MANTER_AMBAS))))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:RESOLUCAO_SOBREPOSICAO_INVALIDA"));

    consultar(token, "2026-12", "2026-12")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' }", hasSize(2))
        .body(
            "itens.find { it.dataFinanceira == '2026-12-10' && it.protegida }.valor",
            equalTo(new BigDecimal("-70.0000")));
    alterar(
            token,
            chave,
            alteracao(
                versao,
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200);
  }

  @Test
  void versaoObsoletaRespondeConflitoSemDadosFinanceiros() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var versaoAntiga = (String) outubro.get("versaoEdicao");
    var chave = (String) outubro.get("chaveEdicao");
    moverOcorrencia(token, "2026-11-10", "2026-11-12", "70");

    alterar(
            token,
            chave,
            alteracao(
                versaoAntiga,
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "999",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(409)
        .body("type", equalTo("urn:economize:problem:VERSAO_DESATUALIZADA"))
        .body("valor", nullValue())
        .body("chaveEdicao", nullValue());
  }

  @Test
  void previaComVersaoAnteriorAoPutRespondeConflitoSemGravarParcialmente() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var versao = (String) outubro.get("versaoEdicao");
    var chave = (String) outubro.get("chaveEdicao");
    previa(
            token,
            chave,
            previaCorpo(
                versao,
                MENSAL_DIA_10,
                ESTA,
                "PLANEJADA",
                "2026-10-10",
                "120",
                "2026-10",
                "2026-12"))
        .statusCode(200)
        .body("versaoEdicao", equalTo(versao));
    moverOcorrencia(token, "2026-11-10", "2026-12-10", "70");
    var antes = consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString();

    alterar(
            token,
            chave,
            alteracao(
                versao,
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(409)
        .body("type", equalTo("urn:economize:problem:VERSAO_DESATUALIZADA"))
        .body("valor", nullValue())
        .body("chaveEdicao", nullValue())
        .body("dataFinanceira", nullValue());

    assertEquals(
        antes, consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString());
  }

  @Test
  void corpoAcimaDoLimiteNoPutRetornaDadosInvalidosSemGravar() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var antes = consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString();
    var corpo =
        alteracao(
            (String) outubro.get("versaoEdicao"),
            ESTA,
            MENSAL_DIA_10,
            "PLANEJADA",
            "2026-10-10",
            "120",
            MANTER_PRESERVADA,
            "[]");
    var acimaDoLimite = "{" + " ".repeat(TAMANHO_CORPO_ACIMA_DO_LIMITE) + corpo.substring(1);

    alterar(token, (String) outubro.get("chaveEdicao"), acimaDoLimite)
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));

    assertEquals(
        antes, consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString());
  }

  @Test
  void listaDeEscolhasAcimaDoLimiteRetornaDadosInvalidosSemGravar() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-11-10", "2026-12-10", "70");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var antes = consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString();
    var acimaDoLimite =
        escolhas(
            Collections.nCopies(257, escolha("2026-12-10", MANTER_AMBAS)).toArray(String[]::new));

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                acimaDoLimite))
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"))
        .body("errors[0].field", equalTo("resolucoesSobreposicao"));

    assertEquals(
        antes, consultar(token, "2026-09", "2026-12").statusCode(200).extract().asString());
  }

  @Test
  void dataDeInicioAnteriorAOriginalNaoEhAceita() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_5,
                "PLANEJADA",
                "2026-11-05",
                "100",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:REPETICAO_INVALIDA"));
  }

  @Test
  void recorrenciaParaParcelamentoExigeNumeracaoENovoGrupo() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var grupoOriginal = criada.extract().path("serie.grupoId");
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                ESTA,
                repeticao(
                    "MENSAL",
                    "[]",
                    "[]",
                    "AJUSTAR_ULTIMO_DIA_MES",
                    "null",
                    "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}"),
                "PLANEJADA",
                "2026-11-10",
                "50",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200)
        .body("serie.numeroParcela", equalTo(1))
        .body("serie.totalParcelas", equalTo(3))
        .body("serie.grupoId", not(equalTo(grupoOriginal)));

    consultar(token, "2026-11", "2027-02")
        .statusCode(200)
        .body("itens.find { it.dataFinanceira == '2026-12-10' }.serie.numeroParcela", equalTo(2))
        .body("itens.find { it.dataFinanceira == '2027-01-10' }.serie.numeroParcela", equalTo(3))
        .body("itens.findAll { it.dataFinanceira == '2027-02-10' }", empty());
  }

  @Test
  void parcelamentoComNovoTotalCriaNovoGrupoPreservandoHistorico() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, PARCELAS_TRES)).statusCode(201);
    var grupoOriginal = criada.extract().path("serie.grupoId");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                repeticao(
                    "MENSAL",
                    "[]",
                    "[]",
                    "AJUSTAR_ULTIMO_DIA_MES",
                    "null",
                    "{\"numeroPrimeiraParcela\":2,\"totalParcelas\":5}"),
                "PLANEJADA",
                "2026-10-10",
                "100",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200)
        .body("serie.numeroParcela", equalTo(2))
        .body("serie.totalParcelas", equalTo(5))
        .body("serie.grupoId", not(equalTo(grupoOriginal)));

    consultar(token, "2026-09", "2027-02")
        .statusCode(200)
        .body("itens.find { it.dataFinanceira == '2026-09-10' }.serie.totalParcelas", equalTo(3))
        .body("itens.find { it.dataFinanceira == '2026-10-10' }.serie.totalParcelas", equalTo(5))
        .body("itens.find { it.dataFinanceira == '2027-01-10' }.serie.numeroParcela", equalTo(5))
        .body("itens.findAll { it.dataFinanceira == '2027-02-10' }", empty());
  }

  @Test
  void parcelamentoComMesmoTotalPreservaGrupoENumeracao() {
    var token = autenticar();
    var criada = criar(token, corpo("PLANEJADA", INICIO, PARCELAS_TRES)).statusCode(201);
    var grupoOriginal = criada.extract().path("serie.grupoId");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                repeticao(
                    "MENSAL",
                    "[]",
                    "[]",
                    "AJUSTAR_ULTIMO_DIA_MES",
                    "null",
                    "{\"numeroPrimeiraParcela\":2,\"totalParcelas\":3}"),
                "PLANEJADA",
                "2026-10-10",
                "100",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200)
        .body("serie.grupoId", equalTo(grupoOriginal))
        .body("serie.numeroParcela", equalTo(2));

    consultar(token, "2026-09", "2026-12")
        .statusCode(200)
        .body("itens.find { it.dataFinanceira == '2026-11-10' }.serie.numeroParcela", equalTo(3))
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' }", empty());
  }

  @Test
  void parcelamentoComNumeracaoDivergenteDaSelecionadaRespondeErroDeParcelamento() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, PARCELAS_TRES)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                repeticao(
                    "MENSAL",
                    "[]",
                    "[]",
                    "AJUSTAR_ULTIMO_DIA_MES",
                    "null",
                    "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}"),
                "PLANEJADA",
                "2026-10-10",
                "100",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:PARCELAMENTO_INVALIDO"));
  }

  @Test
  void parcelamentoParaRecorrenciaExigeTerminoENumeracaoAnteriorNoHistorico() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, PARCELAS_TRES)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "100",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200)
        .body("serie.numeroParcela", nullValue())
        .body("serie.totalParcelas", nullValue());

    consultar(token, "2026-09", "2026-11")
        .statusCode(200)
        .body("itens.find { it.dataFinanceira == '2026-09-10' }.serie.numeroParcela", equalTo(1))
        .body("itens.find { it.dataFinanceira == '2026-09-10' }.serie.totalParcelas", equalTo(3))
        .body("itens.findAll { it.dataFinanceira == '2026-11-10' }", hasSize(1))
        .body("itens.find { it.dataFinanceira == '2026-11-10' }.serie.numeroParcela", nullValue());
  }

  @Test
  void colisaoEmDataFarDaJanelaNaoExpandeRepeticaoInfinita() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-11-10", "2030-01-10", "70");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200);

    consultar(token, "2030-01", "2030-01")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2030-01-10' }", hasSize(1))
        .body(
            "itens.find { it.dataFinanceira == '2030-01-10' }.valor",
            equalTo(new BigDecimal("-70.0000")));
  }

  @Test
  void previaDeCorteParaSimplesNaoListaCandidatasNemSobreposicoes() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    previa(
            token,
            (String) novembro.get("chaveEdicao"),
            previaCorpo(
                (String) novembro.get("versaoEdicao"),
                "null",
                ESTA,
                "PLANEJADA",
                "2026-11-10",
                "80",
                "2026-11",
                "2026-12"))
        .statusCode(200)
        .body("ocorrenciasNovas", empty())
        .body("sobreposicoes", empty())
        .body("quantidadeSobreposicoesForaPeriodo", equalTo(0));
  }

  @Test
  void previaComDataInicialAnteriorAOriginalRespondeRepeticaoInvalida() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");

    previa(
            token,
            (String) novembro.get("chaveEdicao"),
            previaCorpo(
                (String) novembro.get("versaoEdicao"),
                MENSAL_DIA_5,
                ESTA,
                "PLANEJADA",
                "2026-11-05",
                "80",
                "2026-11",
                "2026-12"))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:REPETICAO_INVALIDA"));
  }

  @Test
  void regraSemanalDeveIncluirDiaDaDataDeInicio() {
    var token = autenticar();
    // 2026-09-09 é quarta-feira; a regra semanal só possui segunda-feira.
    criar(
            token,
            corpo(
                "PLANEJADA",
                "2026-09-09",
                repeticao(
                    "SEMANAL",
                    "[\"SEGUNDA\"]",
                    "[]",
                    "PADRAO",
                    "{\"tipo\":\"INDEFINIDO\"}",
                    "null")))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:REPETICAO_INVALIDA"));
  }

  @Test
  void regraMensalPadraoDeveIncluirDiaDaDataDeInicio() {
    var token = autenticar();
    criar(
            token,
            corpo(
                "PLANEJADA",
                INICIO,
                repeticao("MENSAL", "[]", "[5]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null")))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:REPETICAO_INVALIDA"));
  }

  @Test
  void agrupaVariasPreservadasDaMesmaDataEmUmaUnicaDecisao() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-11-10", "2026-12-10", "70");
    var dezembro = virtualDe(token, "2026-12", "2026-12-10");
    alterar(
            token,
            (String) dezembro.get("chaveEdicao"),
            alteracao(
                (String) dezembro.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "PLANEJADA",
                "2026-12-10",
                "90",
                null,
                null))
        .statusCode(200);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                MENSAL_DIA_10,
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200);

    consultar(token, "2026-12", "2026-12")
        .statusCode(200)
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' }", hasSize(2))
        .body("itens.findAll { it.dataFinanceira == '2026-12-10' && it.protegida }", hasSize(2));
  }

  @Test
  void escritoresConcorrentesComMesmaVersaoTemUmSoVencedor() throws Exception {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    var chave = (String) outubro.get("chaveEdicao");
    var versao = (String) outubro.get("versaoEdicao");
    var corpoA =
        alteracao(
            versao, ESTA, MENSAL_DIA_10, "PLANEJADA", "2026-10-10", "110", MANTER_PRESERVADA, "[]");
    var corpoB =
        alteracao(
            versao, ESTA, MENSAL_DIA_10, "PLANEJADA", "2026-10-10", "130", MANTER_PRESERVADA, "[]");
    var largada = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> primeiro =
          () -> {
            largada.await();
            return alterar(token, chave, corpoA).extract().statusCode();
          };
      Callable<Integer> segundo =
          () -> {
            largada.await();
            return alterar(token, chave, corpoB).extract().statusCode();
          };
      var resultadoA = executor.submit(primeiro);
      var resultadoB = executor.submit(segundo);
      largada.countDown();
      var statuses =
          List.of(resultadoA.get(60, TimeUnit.SECONDS), resultadoB.get(60, TimeUnit.SECONDS));
      // A chave da ocorrência cortada deixa de existir após o primeiro PUT: o perdedor recebe 404
      // (ou 409, se a chave ainda for válida), e apenas uma escrita é aplicada.
      assertEquals(
          1, statuses.stream().filter(status -> status == 200).count(), "statuses=" + statuses);
      assertEquals(
          1,
          statuses.stream().filter(status -> status == 404 || status == 409).count(),
          "statuses=" + statuses);
    } finally {
      executor.shutdownNow();
    }
  }

  private Map<String, Object> virtualDe(String token, String mes, String data) {
    Map<String, Object> item =
        consultar(token, mes, mes)
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("itens.find { it.dataFinanceira == '" + data + "' && !it.protegida }");
    assertNotNull(item, "virtual de " + data);
    return item;
  }

  private void moverOcorrencia(String token, String dataOriginal, String novaData, String valor) {
    var mes = dataOriginal.substring(0, 7);
    var item = itemDe(token, mes, mes, dataOriginal);
    alterar(
            token,
            (String) item.get("chaveEdicao"),
            alteracao(
                (String) item.get("versaoEdicao"),
                SOMENTE_ESTA,
                "null",
                "PLANEJADA",
                novaData,
                valor,
                null,
                null))
        .statusCode(200);
  }

  @Test
  void previaReportaMesmaSobreposicaoQueOPutResolve() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", INICIO, MENSAL_DIA_10)).statusCode(201);
    moverOcorrencia(token, "2026-11-10", "2026-12-10", "70");
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    previa(
            token,
            (String) outubro.get("chaveEdicao"),
            previaCorpo(
                (String) outubro.get("versaoEdicao"),
                MENSAL_DIA_10,
                ESTA,
                "PLANEJADA",
                "2026-10-10",
                "120",
                "2026-10",
                "2026-12"))
        .statusCode(200)
        .body("sobreposicoes", hasSize(1))
        .body("sobreposicoes[0].dataFinanceira", equalTo("2026-12-10"))
        .body("sobreposicoes[0].dataOriginalCandidata", equalTo("2026-12-10"))
        .body("sobreposicoes[0].chavesPreservadas", hasSize(1))
        .body("sobreposicoes[0].resolucaoSugerida", equalTo(MANTER_PRESERVADA));
  }

  @Test
  void formaMensalAncoradaDeSegmentoExistenteAceitaSomenteNaAlteracao() {
    var token = autenticar();
    var segmentoId = criarSegmentoLegadoMensalSemDias(LocalDate.of(2026, 9, 10));
    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");

    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                ESTA,
                repeticao("MENSAL", "[]", "[]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null"),
                "PLANEJADA",
                "2026-10-10",
                "120",
                MANTER_PRESERVADA,
                "[]"))
        .statusCode(200)
        .body("serie.segmentoId", not(equalTo(segmentoId.toString())))
        .body("repeticao.diasMes", empty())
        .body("repeticao.politicaData", equalTo("PADRAO"));

    criar(
            token,
            corpo(
                "PLANEJADA",
                INICIO,
                repeticao("MENSAL", "[]", "[]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null")))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:REPETICAO_INVALIDA"));
  }

  private UUID criarSegmentoLegadoMensalSemDias(LocalDate inicio) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              var usuario = usuarioRepository.findById(usuarioId);
              var conta = contaRepository.findById(contaId);
              var grupo = new GrupoRecorrencia();
              grupo.setUsuario(usuario);
              grupo.setDescricao("Aluguel");
              grupo.setTipo(TipoGrupoRecorrencia.RECORRENCIA);
              grupo.setStatus(StatusRecorrencia.ATIVO);
              grupoRepository.persist(grupo);

              var segmento = new SegmentoRecorrencia();
              segmento.setUsuario(usuario);
              segmento.setGrupo(grupo);
              segmento.setConta(conta);
              segmento.setTipo(TipoTransacao.DESPESA);
              segmento.setDescricao("Aluguel");
              segmento.setValor(new BigDecimal("100.0000"));
              segmento.setInicio(inicio);
              segmento.setRrule("FREQ=MONTHLY");
              segmento.setPoliticaData(PoliticaDataOcorrencia.PADRAO);
              segmento.setStatus(StatusRecorrencia.ATIVO);
              segmentoRepository.persist(segmento);
              return segmento.getId();
            });
  }

  private ValidatableResponse efetivarOcorrencia(String token, Object segmentoId, String data) {
    var mes = data.substring(0, 7);
    var item = itemDe(token, mes, mes, data);
    return alterar(
        token,
        (String) item.get("chaveEdicao"),
        alteracao(
            (String) item.get("versaoEdicao"),
            "SOMENTE_ESTA",
            "null",
            "EFETIVADA",
            data,
            "100.00",
            null,
            null));
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

  private Map<String, Object> itemDe(String token, String inicio, String fim, String data) {
    Map<String, Object> item =
        consultar(token, inicio, fim)
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("itens.find { it.dataFinanceira == '" + data + "' }");
    assertNotNull(item, "item de " + data);
    assertNotEquals(null, item.get("chaveEdicao"));
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

  private String previaCorpo(
      String versao,
      String repeticao,
      String escopo,
      String situacao,
      String dataFinanceira,
      String valor,
      String inicio,
      String fim) {
    return """
    {"alteracao":%s,"periodo":{"inicio":"%s","fim":"%s"},
     "politicaSobreposicao":"MANTER_SOMENTE_PRESERVADA"}
    """
        .formatted(
            alteracao(versao, escopo, repeticao, situacao, dataFinanceira, valor, null, null),
            inicio,
            fim);
  }

  private static String escolha(String dataOriginalCandidata, String resolucao) {
    return "{\"dataOriginalCandidata\":\""
        + dataOriginalCandidata
        + "\",\"resolucao\":\""
        + resolucao
        + "\"}";
  }

  private static String escolhas(String... itens) {
    var lista = new ArrayList<String>();
    for (var item : itens) {
      lista.add(item);
    }
    return "[" + String.join(",", lista) + "]";
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
