package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConverterTransacaoEmSerieResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");
  private static final String REPETICAO_MENSAL_DIA_10_INDEFINIDA =
      repeticao("MENSAL", "[]", "[10]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null");
  private static final String PARCELAMENTO_MENSAL_3_DE_5 =
      repeticao(
          "MENSAL",
          "[]",
          "[]",
          "AJUSTAR_ULTIMO_DIA_MES",
          "null",
          "{\"numeroPrimeiraParcela\":3,\"totalParcelas\":5}");

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;
  @Inject TransacaoRepository transacaoRepository;
  @Inject GrupoRecorrenciaRepository grupoRepository;
  @Inject SegmentoRecorrenciaRepository segmentoRepository;

  private String email;
  private String emailOutroUsuario;
  private UUID usuarioId;
  private UUID contaId;

  @BeforeEach
  void prepararDados() {
    email = "conversao-" + UUID.randomUUID() + "@example.com";
    emailOutroUsuario = "conversao-outra-" + UUID.randomUUID() + "@example.com";
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
              usuarioId = usuario.getId();
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

  @AfterEach
  void limparFalhaSimulada() {
    removerFalhaSimulada();
  }

  @Test
  void converteSimplesPlanejadaEmRepeticaoSemDuplicarOMovimento() {
    var token = autenticar();
    var criada = criarSimples(token, "PLANEJADA", "2026-11-10");
    var chaveAntiga = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();

    var convertida =
        alterar(
                token,
                chaveAntiga,
                corpoEdicao(
                    versao, "PLANEJADA", "2026-11-10", REPETICAO_MENSAL_DIA_10_INDEFINIDA, ""))
            .statusCode(200)
            .body("chaveEdicao", notNullValue())
            .body("versaoEdicao", notNullValue())
            .body("origem", equalTo("TRANSACAO_RECORRENTE"))
            .body("situacao", equalTo("PLANEJADA"))
            .body("virtual", equalTo(false))
            .body("protegida", equalTo(false))
            .body("efetivadoEm", nullValue())
            .body("serie.dataOriginal", equalTo("2026-11-10"))
            .body("serie.numeroParcela", nullValue())
            .body("repeticao.frequencia", equalTo("MENSAL"))
            .body("repeticao.termino.tipo", equalTo("INDEFINIDO"))
            .body("escoposDisponiveis", hasSize(2))
            .extract()
            .path("chaveEdicao");
    assertNotEquals(chaveAntiga, convertida);

    alterar(token, chaveAntiga, corpoEdicao(versao, "PLANEJADA", "2026-11-10", "null", ""))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));

    consultar(token, "2026-11", "2026-12")
        .statusCode(200)
        .body("itens", hasSize(2))
        .body("itens.dataFinanceira", contains("2026-11-10", "2026-12-10"))
        .body("itens[0].origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("itens[0].virtual", equalTo(false))
        .body("itens[0].chaveEdicao", equalTo(convertida))
        .body("itens[1].virtual", equalTo(true));
    assertEquals(1, contarTransacoesDoUsuario());
    assertEquals(1, contarSegmentosDoUsuario());
    assertEquals(1, contarGruposDoUsuario());
  }

  @Test
  void converteSimplesEfetivadaEmParcelamentoPreservandoAEfetivacao() {
    var token = autenticar();
    var criada = criarSimples(token, "EFETIVADA", "2026-10-10");
    var chaveAntiga = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();
    var efetivadoAntes =
        consultar(token, "2026-10", "2026-10")
            .statusCode(200)
            .extract()
            .path("itens[0].efetivadoEm")
            .toString();

    alterar(
            token,
            chaveAntiga,
            corpoEdicao(versao, "EFETIVADA", "2026-10-10", PARCELAMENTO_MENSAL_3_DE_5, ""))
        .statusCode(200)
        .body("origem", equalTo("PARCELA"))
        .body("protegida", equalTo(true))
        .body("virtual", equalTo(false))
        .body("situacao", equalTo("EFETIVADA"))
        .body("efetivadoEm", equalTo(efetivadoAntes))
        .body("serie.numeroParcela", equalTo(3))
        .body("serie.totalParcelas", equalTo(5))
        .body("repeticao.parcelamento.numeroPrimeiraParcela", equalTo(3))
        .body("repeticao.parcelamento.totalParcelas", equalTo(5));

    consultar(token, "2026-10", "2027-01")
        .statusCode(200)
        .body("itens", hasSize(3))
        .body("itens.dataFinanceira", contains("2026-10-10", "2026-11-10", "2026-12-10"))
        .body("itens.serie.numeroParcela", contains(3, 4, 5))
        .body("itens.serie.totalParcelas", contains(5, 5, 5))
        .body("itens[0].efetivadoEm", equalTo(efetivadoAntes))
        .body("itens[1].efetivadoEm", nullValue());
    consultar(token, "2026-11", "2026-11")
        .statusCode(200)
        .body("saldoAbertura", equalTo(new BigDecimal("-1500.0000")))
        .body("itens", hasSize(1))
        .body("itens.dataFinanceira", contains("2026-11-10"));
    assertEquals(1, contarTransacoesDoUsuario());
    assertEquals(1, contarSegmentosDoUsuario());
  }

  @Test
  void planejadaConvertidaParaEfetivadaRegistraInstanteEMantemOValorNaConsulta() {
    var token = autenticar();
    var criada = criarSimples(token, "PLANEJADA", "2026-10-10");
    var chaveAntiga = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();

    alterar(
            token,
            chaveAntiga,
            corpoEdicao(versao, "EFETIVADA", "2026-10-10", REPETICAO_MENSAL_DIA_10_INDEFINIDA, ""))
        .statusCode(200)
        .body("situacao", equalTo("EFETIVADA"))
        .body("efetivadoEm", notNullValue())
        .body("protegida", equalTo(true));
    consultar(token, "2026-11", "2026-11")
        .statusCode(200)
        .body("saldoAbertura", equalTo(new BigDecimal("-1500.0000")));
  }

  @Test
  void replanejadaNaConversaoLimpaOInstanteDeEfetivacao() {
    var token = autenticar();
    var criada = criarSimples(token, "EFETIVADA", "2026-10-10");
    var chave = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();

    alterar(
            token,
            chave,
            corpoEdicao(
                versao, "PLANEJADA", "2026-10-10", REPETICAO_MENSAL_DIA_10_INDEFINIDA, ""))
        .statusCode(200)
        .body("situacao", equalTo("PLANEJADA"))
        .body("efetivadoEm", nullValue())
        .body("protegida", equalTo(false));
    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens", hasSize(1))
        .body("itens[0].efetivadoEm", nullValue())
        .body("itens[0].situacao", equalTo("PLANEJADA"));
  }

  @Test
  void rejeitaEfetivacaoComDataFuturaSemConverterATransacao() {
    var token = autenticar();
    var criada = criarSimples(token, "PLANEJADA", "2026-10-10");
    var chaveAntiga = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();
    var futura = LocalDate.now(FUSO).plusDays(1).toString();

    alterar(
            token,
            chaveAntiga,
            corpoEdicao(versao, "EFETIVADA", futura, REPETICAO_MENSAL_DIA_10_INDEFINIDA, ""))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_FUTURA"));
    assertEquals(0, contarSegmentosDoUsuario());
    consultar(token, "2026-10", "2026-10")
        .statusCode(200)
        .body("itens", hasSize(1))
        .body("itens[0].origem", equalTo("TRANSACAO_SIMPLES"))
        .body("itens[0].chaveEdicao", equalTo(chaveAntiga));
  }

  @Test
  void rejeitaVersaoObsoletaSemConverterATransacao() {
    var token = autenticar();
    var criada = criarSimples(token, "PLANEJADA", "2026-11-10");
    var chave = criada.path("chaveEdicao").toString();
    var versaoOriginal = criada.path("versaoEdicao").toString();

    var atualizada =
        alterar(token, chave, corpoEdicao(versaoOriginal, "PLANEJADA", "2026-11-10", "null", ""))
            .statusCode(200)
            .extract()
            .response();
    assertNotEquals(versaoOriginal, atualizada.path("versaoEdicao").toString());

    alterar(
            token,
            chave,
            corpoEdicao(
                versaoOriginal, "PLANEJADA", "2026-11-10", REPETICAO_MENSAL_DIA_10_INDEFINIDA, ""))
        .statusCode(409)
        .body("type", equalTo("urn:economize:problem:VERSAO_DESATUALIZADA"));
    assertEquals(0, contarSegmentosDoUsuario());
  }

  @Test
  void rejeitaEscopoEDecisoesDeSobreposicaoNaConversaoSimples() {
    var token = autenticar();
    var criada = criarSimples(token, "PLANEJADA", "2026-11-10");
    var chave = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();

    alterar(
            token,
            chave,
            corpoEdicao(
                versao,
                "PLANEJADA",
                "2026-11-10",
                REPETICAO_MENSAL_DIA_10_INDEFINIDA,
                ",\"escopo\":\"SOMENTE_ESTA\""))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:ESCOPO_INVALIDO"));
    alterar(
            token,
            chave,
            corpoEdicao(
                versao,
                "PLANEJADA",
                "2026-11-10",
                REPETICAO_MENSAL_DIA_10_INDEFINIDA,
                ",\"politicaSobreposicao\":\"MANTER_SOMENTE_PRESERVADA\",\"resolucoesSobreposicao\":[]"))
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));
    assertEquals(0, contarSegmentosDoUsuario());
  }

  @Test
  void rejeitaRepeticaoOuParcelamentoInvalidosSemAlterarATransacao() {
    var token = autenticar();
    var criada = criarSimples(token, "PLANEJADA", "2026-11-10");
    var chave = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();

    alterar(
            token,
            chave,
            corpoEdicao(
                versao,
                "PLANEJADA",
                "2026-11-10",
                repeticao("SEMANAL", "[]", "[]", "PADRAO", "{\"tipo\":\"INDEFINIDO\"}", "null"),
                ""))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:REPETICAO_INVALIDA"));
    alterar(
            token,
            chave,
            corpoEdicao(
                versao,
                "PLANEJADA",
                "2026-11-10",
                repeticao(
                    "MENSAL",
                    "[]",
                    "[]",
                    "AJUSTAR_ULTIMO_DIA_MES",
                    "null",
                    "{\"numeroPrimeiraParcela\":6,\"totalParcelas\":5}"),
                ""))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:PARCELAMENTO_INVALIDO"));
    alterar(
            token,
            chave,
            corpoEdicao(
                versao,
                "PLANEJADA",
                "2026-11-10",
                repeticao(
                    "MENSAL",
                    "[]",
                    "[]",
                    "AJUSTAR_ULTIMO_DIA_MES",
                    "{\"tipo\":\"INDEFINIDO\"}",
                    "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":5}"),
                ""))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:PARCELAMENTO_INVALIDO"));
    assertEquals(0, contarSegmentosDoUsuario());
    consultar(token, "2026-11", "2026-11")
        .statusCode(200)
        .body("itens[0].origem", equalTo("TRANSACAO_SIMPLES"))
        .body("itens[0].chaveEdicao", equalTo(chave));
  }

  @Test
  void ocultaTransacaoAlheiaNaConversao() {
    var token = autenticar();
    var criada = criarSimples(token, "PLANEJADA", "2026-11-10");
    var chave = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();

    alterar(
            autenticarOutroUsuario(),
            chave,
            corpoEdicao(versao, "PLANEJADA", "2026-11-10", REPETICAO_MENSAL_DIA_10_INDEFINIDA, ""))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    assertEquals(0, contarSegmentosDoUsuario());
  }

  @Test
  void falhaNaAtualizacaoDesfazAConversaoInteira() {
    var token = autenticar();
    var criada = criarSimples(token, "PLANEJADA", "2026-11-10");
    var chave = criada.path("chaveEdicao").toString();
    var versao = criada.path("versaoEdicao").toString();
    instalarFalhaNaAtualizacaoDaTransacao();

    alterar(
            token,
            chave,
            corpoEdicao(versao, "PLANEJADA", "2026-11-10", REPETICAO_MENSAL_DIA_10_INDEFINIDA, "")
                .replace("\"descricao\":\"Aluguel\"", "\"descricao\":\"falha simulada\""))
        .statusCode(500);
    assertEquals(0, contarSegmentosDoUsuario());
    assertEquals(0, contarGruposDoUsuario());
    consultar(token, "2026-11", "2026-11")
        .statusCode(200)
        .body("itens[0].origem", equalTo("TRANSACAO_SIMPLES"))
        .body("itens[0].chaveEdicao", equalTo(chave))
        .body("itens[0].versaoEdicao", equalTo(versao));

    removerFalhaSimulada();
    alterar(
            token,
            chave,
            corpoEdicao(versao, "PLANEJADA", "2026-11-10", REPETICAO_MENSAL_DIA_10_INDEFINIDA, ""))
        .statusCode(200)
        .body("origem", equalTo("TRANSACAO_RECORRENTE"));
    assertEquals(1, contarSegmentosDoUsuario());
  }

  private void instalarFalhaNaAtualizacaoDaTransacao() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              var em = transacaoRepository.getEntityManager();
              em.createNativeQuery(
                      """
                      CREATE OR REPLACE FUNCTION falha_conversao_teste() RETURNS trigger AS $$
                      BEGIN
                        IF NEW.STR_DESCRICAO = 'falha simulada' THEN
                          RAISE EXCEPTION 'falha simulada';
                        END IF;
                        RETURN NEW;
                      END;
                      $$ LANGUAGE plpgsql
                      """)
                  .executeUpdate();
              em.createNativeQuery(
                      """
                      CREATE TRIGGER falha_conversao_teste BEFORE UPDATE ON TB006_TRANSACAO
                      FOR EACH ROW EXECUTE FUNCTION falha_conversao_teste()
                      """)
                  .executeUpdate();
            });
  }

  private void removerFalhaSimulada() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              var em = transacaoRepository.getEntityManager();
              em.createNativeQuery(
                      "DROP TRIGGER IF EXISTS falha_conversao_teste ON TB006_TRANSACAO")
                  .executeUpdate();
              em.createNativeQuery("DROP FUNCTION IF EXISTS falha_conversao_teste()")
                  .executeUpdate();
            });
  }

  private long contarTransacoesDoUsuario() {
    return QuarkusTransaction.requiringNew()
        .call(() -> transacaoRepository.count("usuario.id = ?1", usuarioId));
  }

  private long contarSegmentosDoUsuario() {
    return QuarkusTransaction.requiringNew()
        .call(() -> segmentoRepository.count("usuario.id = ?1", usuarioId));
  }

  private long contarGruposDoUsuario() {
    return QuarkusTransaction.requiringNew()
        .call(() -> grupoRepository.count("usuario.id = ?1", usuarioId));
  }

  private io.restassured.response.Response criarSimples(
      String token, String situacao, String dataFinanceira) {
    return given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {"tipo":"DESPESA","contaId":"%s","categoriaId":null,"descricao":"Aluguel",
             "observacoes":null,"valor":1500.00,"dataFinanceira":"%s","situacao":"%s",
             "repeticao":null}
            """
                .formatted(contaId, dataFinanceira, situacao))
        .when()
        .post("/api/transacoes")
        .then()
        .statusCode(201)
        .extract()
        .response();
  }

  private ValidatableResponse alterar(String token, String chave, String corpo) {
    return given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(corpo)
        .when()
        .put("/api/transacoes/" + chave)
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

  private String corpoEdicao(
      String versao, String situacao, String dataFinanceira, String repeticao, String extras) {
    return """
    {"tipo":"DESPESA","contaId":"%s","categoriaId":null,"descricao":"Aluguel",
     "observacoes":null,"valor":1500.00,"dataFinanceira":"%s","situacao":"%s",
     "versaoEdicao":"%s","repeticao":%s%s}
    """
        .formatted(contaId, dataFinanceira, situacao, versao, repeticao, extras);
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
}
