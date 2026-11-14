package com.joaovpg.economize.transferencia;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransferenciaResourceTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private String token;
  private UUID contaOrigemId;
  private UUID contaDestinoId;
  private UUID categoriaId;

  @BeforeEach
  void preparar() {
    token = cadastrarUsuario("transferencia-" + UUID.randomUUID() + "@example.com");
    contaOrigemId = cadastrarConta(token, "Conta origem", "BRL");
    contaDestinoId = cadastrarConta(token, "Conta destino", "BRL");
    categoriaId = cadastrarCategoria(token);
  }

  @Test
  void criaTransferenciaEExibeLadosNaLinhaDoTempo() {
    var data = LocalDate.now();
    var transferenciaId =
        criarTransferencia("PLANEJADA", data)
            .statusCode(201)
            .body("transferenciaId", notNullValue())
            .body("contaOrigemId", equalTo(contaOrigemId.toString()))
            .body("contaDestinoId", equalTo(contaDestinoId.toString()))
            .body("situacao", equalTo("PLANEJADA"))
            .body("efetivadoEm", nullValue())
            .extract()
            .jsonPath()
            .getUUID("transferenciaId");

    var mes = YearMonth.from(data).toString();
    given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .queryParam("inicio", mes)
        .queryParam("fim", mes)
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(2))
        .body("itens[0].origem", equalTo("TRANSFERENCIA"))
        .body("itens[0].operacaoId", equalTo(transferenciaId.toString()))
        .body("itens[0].tipo", nullValue())
        .body("itens[0].valor", equalTo(new BigDecimal("-125.0000")))
        .body("itens[0].contaId", equalTo(contaOrigemId.toString()))
        .body("itens[0].contaContraparteId", equalTo(contaDestinoId.toString()))
        .body("itens[0].categoriaId", nullValue())
        .body("itens[1].operacaoId", equalTo(transferenciaId.toString()))
        .body("itens[1].tipo", nullValue())
        .body("itens[1].valor", equalTo(new BigDecimal("125.0000")))
        .body("itens[1].contaId", equalTo(contaDestinoId.toString()))
        .body("itens[1].contaContraparteId", equalTo(contaOrigemId.toString()))
        .body("itens[1].categoriaId", nullValue())
        .body("saldoAbertura", equalTo(new BigDecimal("0.0000")));
  }

  @Test
  void criaComContratoCompletoEInformaLocalDeEdicao() {
    var data = LocalDate.now();
    var resposta =
        criarComCorpo(token, corpo(contaOrigemId, contaDestinoId, "EFETIVADA", "Reserva", data))
            .statusCode(201)
            .body("transferenciaId", notNullValue())
            .body("versaoEdicao", notNullValue())
            .body("descricao", equalTo("Reserva"))
            .body("observacoes", equalTo("Observacao"))
            .body("valor", equalTo(125.0F))
            .body("dataFinanceira", equalTo(data.toString()))
            .body("efetivadoEm", notNullValue())
            .extract()
            .response();

    var id = UUID.fromString(resposta.path("transferenciaId"));
    assertTrue(resposta.header("Location").endsWith("/api/transferencias/" + id));
  }

  @Test
  void rejeitaCamposForaDoContratoEAusentesNaCriacaoEnaEdicao() {
    var criada = criarTransferencia("PLANEJADA", LocalDate.now()).statusCode(201).extract();
    var id = UUID.fromString(criada.path("transferenciaId"));
    String versao = criada.path("versaoEdicao");

    for (var extra :
        List.<Map.Entry<String, Object>>of(
            Map.entry("categoriaId", categoriaId.toString()),
            Map.entry("repeticao", Map.of("frequencia", "MENSAL")),
            Map.entry("parcelamento", Map.of("totalParcelas", 2)))) {
      var corpo = corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Reserva", LocalDate.now());
      corpo.put(extra.getKey(), extra.getValue());
      criarComCorpo(token, corpo)
          .statusCode(400)
          .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));
      alterarComCorpo(token, id, comVersao(corpo, versao))
          .statusCode(400)
          .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));
    }

    var semObservacoesNaCriacao =
        corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Sem observacao", LocalDate.now());
    semObservacoesNaCriacao.remove("observacoes");
    criarComCorpo(token, semObservacoesNaCriacao).statusCode(201).body("observacoes", nullValue());

    var semObservacoesNaEdicao =
        comVersao(
            corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Reserva", LocalDate.now()), versao);
    semObservacoesNaEdicao.remove("observacoes");
    alterarComCorpo(token, id, semObservacoesNaEdicao)
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));

    var semVersao = corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Reserva", LocalDate.now());
    alterarComCorpo(token, id, semVersao)
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"));

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body("{json invalido")
        .when()
        .post("/api/transferencias")
        .then()
        .statusCode(400);
  }

  @Test
  void editaComVersaoEAceitaSomenteAVersaoVigente() {
    var criada = criarTransferencia("PLANEJADA", LocalDate.now()).statusCode(201).extract();
    var id = UUID.fromString(criada.path("transferenciaId"));
    String versaoOriginal = criada.path("versaoEdicao");

    var editada =
        alterarComCorpo(
                token,
                id,
                comVersao(
                    corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Editada", LocalDate.now()),
                    versaoOriginal))
            .statusCode(200)
            .body("transferenciaId", equalTo(id.toString()))
            .body("descricao", equalTo("Editada"))
            .extract();
    String versaoNova = editada.path("versaoEdicao");
    assertNotEquals(versaoOriginal, versaoNova);

    alterarComCorpo(
            token,
            id,
            comVersao(
                corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Obsoleta", LocalDate.now()),
                versaoOriginal))
        .statusCode(409)
        .body("type", equalTo("urn:economize:problem:VERSAO_DESATUALIZADA"));

    alterarComCorpo(
            token,
            id,
            comVersao(
                corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Vigente", LocalDate.now()),
                versaoNova))
        .statusCode(200)
        .body("descricao", equalTo("Vigente"));
  }

  @Test
  void validaContasDeTerceirosInativasEDataSaldoInicial() {
    var data = LocalDate.now();
    var outroToken = cadastrarUsuario("outra-transferencia-" + UUID.randomUUID() + "@example.com");
    var contaAlheia = cadastrarConta(outroToken, "Conta alheia", "BRL");
    var contaInativa = cadastrarConta(token, "Conta inativa", "BRL");
    editarConta(token, contaInativa, "Conta inativa", false);
    var contaNova = cadastrarConta(token, "Conta nova", "BRL");

    criarComCorpo(token, corpo(contaAlheia, contaDestinoId, "PLANEJADA", "X", data))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    criarComCorpo(token, corpo(contaOrigemId, contaInativa, "PLANEJADA", "X", data))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    criarComCorpo(
            token,
            corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "X", LocalDate.of(2025, 12, 31)))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL"));

    var existente =
        criarComCorpo(token, corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Y", data))
            .statusCode(201)
            .extract();
    var id = UUID.fromString(existente.path("transferenciaId"));
    String versao = existente.path("versaoEdicao");

    alterarComCorpo(
            token, id, comVersao(corpo(contaOrigemId, contaAlheia, "PLANEJADA", "Y", data), versao))
        .statusCode(404);
    alterarComCorpo(
            token,
            id,
            comVersao(corpo(contaOrigemId, contaInativa, "PLANEJADA", "Y", data), versao))
        .statusCode(404);
    alterarComCorpo(
            token,
            id,
            comVersao(corpo(contaOrigemId, contaNova, "PLANEJADA", "Trocada", data), versao))
        .statusCode(200)
        .body("contaDestinoId", equalTo(contaNova.toString()));

    var mes = YearMonth.from(data).toString();
    given()
        .auth()
        .oauth2(token)
        .queryParam("inicio", mes)
        .queryParam("fim", mes)
        .queryParam("contaId", contaNova)
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(1))
        .body("itens[0].contaContraparteId", equalTo(contaOrigemId.toString()));
  }

  @Test
  void efetivacaoCorrecaoEReplanejamentoMantemInstanteNosDoisLados() {
    var data = LocalDate.now();
    var criada = criarTransferencia("PLANEJADA", data).statusCode(201).extract();
    var id = UUID.fromString(criada.path("transferenciaId"));

    var efetivada =
        alterarComCorpo(
                token,
                id,
                comVersao(
                    corpo(contaOrigemId, contaDestinoId, "EFETIVADA", "Efetivada", data),
                    criada.path("versaoEdicao")))
            .statusCode(200)
            .body("situacao", equalTo("EFETIVADA"))
            .body("efetivadoEm", notNullValue())
            .extract();
    String primeiroInstante = efetivada.path("efetivadoEm");
    String versao = efetivada.path("versaoEdicao");

    var corrigida =
        alterarComCorpo(
                token,
                id,
                comVersao(
                    corpo(contaOrigemId, contaDestinoId, "EFETIVADA", "Corrigida", data), versao))
            .statusCode(200)
            .body("efetivadoEm", equalTo(primeiroInstante))
            .extract();
    versao = corrigida.path("versaoEdicao");
    assertLadosComMesmoInstante(data, id, primeiroInstante);

    var replanejada =
        alterarComCorpo(
                token,
                id,
                comVersao(
                    corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Replanejada", data), versao))
            .statusCode(200)
            .body("efetivadoEm", nullValue())
            .extract();
    versao = replanejada.path("versaoEdicao");
    assertLadosComMesmoInstante(data, id, null);

    var novamenteEfetivada =
        alterarComCorpo(
                token,
                id,
                comVersao(
                    corpo(contaOrigemId, contaDestinoId, "EFETIVADA", "Efetivada novamente", data),
                    versao))
            .statusCode(200)
            .extract();
    String segundoInstante = novamenteEfetivada.path("efetivadoEm");
    assertNotEquals(primeiroInstante, segundoInstante);
    assertLadosComMesmoInstante(data, id, segundoInstante);
  }

  @Test
  void leituraExpoeOperacaoVersaoELadosOpostosSemIdsInternos() {
    var data = LocalDate.now();
    var criada = criarTransferencia("PLANEJADA", data).statusCode(201).extract();
    var id = UUID.fromString(criada.path("transferenciaId"));
    String versao = criada.path("versaoEdicao");

    given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .queryParam("inicio", YearMonth.from(data).toString())
        .queryParam("fim", YearMonth.from(data).toString())
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(2))
        .body("itens[0]", not(hasKey("id")))
        .body("itens[0].versaoEdicao", equalTo(versao))
        .body("itens[1].versaoEdicao", equalTo(versao))
        .body("itens[0].operacaoId", equalTo(id.toString()))
        .body("itens[1].operacaoId", equalTo(id.toString()));
  }

  @Test
  void falhaNaEdicaoNaoAlteraNenhumDosLados() {
    var data = LocalDate.now();
    var criada = criarTransferencia("PLANEJADA", data).statusCode(201).extract();
    var id = UUID.fromString(criada.path("transferenciaId"));
    String versao = criada.path("versaoEdicao");

    alterarComCorpo(
            token,
            id,
            comVersao(
                corpo(contaOrigemId, contaDestinoId, "EFETIVADA", "Nao gravar", data.plusDays(1)),
                versao))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_FUTURA"));

    var mes = YearMonth.from(data).toString();
    given()
        .auth()
        .oauth2(token)
        .queryParam("inicio", mes)
        .queryParam("fim", mes)
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(2))
        .body("itens.descricao", everyItem(equalTo("Reserva mensal")))
        .body("itens.situacao", everyItem(equalTo("PLANEJADA")))
        .body("itens.versaoEdicao", everyItem(equalTo(versao)));
  }

  @Test
  void duasEdicoesComMesmaVersaoTerminamUmaComSucessoEOutraEmConflito() throws Exception {
    var criada = criarTransferencia("PLANEJADA", LocalDate.now()).statusCode(201).extract();
    var id = UUID.fromString(criada.path("transferenciaId"));
    String versao = criada.path("versaoEdicao");

    var inicio = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<Integer>> respostas = new java.util.ArrayList<>();
      for (var descricao : List.of("Concorrente A", "Concorrente B")) {
        Callable<Integer> tarefa =
            () -> {
              inicio.await();
              return alterarComCorpo(
                      token,
                      id,
                      comVersao(
                          corpo(
                              contaOrigemId,
                              contaDestinoId,
                              "PLANEJADA",
                              descricao,
                              LocalDate.now()),
                          versao))
                  .extract()
                  .statusCode();
            };
        respostas.add(executor.submit(tarefa));
      }
      inicio.countDown();
      List<Integer> codigos = new java.util.ArrayList<>();
      for (var resposta : respostas) {
        codigos.add(resposta.get(30, TimeUnit.SECONDS));
      }
      assertEquals(List.of(200, 409), codigos.stream().sorted().toList());
    }
  }

  @Test
  void protegeRegrasEIsolamento() {
    var data = LocalDate.now();
    criarTransferenciaComContas(contaOrigemId, contaOrigemId, "PLANEJADA", data)
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:CONTAS_TRANSFERENCIA_IGUAIS"));
    criarTransferencia("EFETIVADA", data.plusDays(1))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_FUTURA"));

    var transferenciaId =
        criarTransferencia("PLANEJADA", data)
            .statusCode(201)
            .extract()
            .jsonPath()
            .getUUID("transferenciaId");
    var outroToken = cadastrarUsuario("outra-transferencia-" + UUID.randomUUID() + "@example.com");
    given()
        .auth()
        .oauth2(outroToken)
        .queryParam("versaoEdicao", "versao-qualquer")
        .when()
        .delete("/api/transferencias/{id}", transferenciaId)
        .then()
        .statusCode(404);
  }

  @Test
  void excluirTransferenciaComVersaoObsoletaRespondeConflitoSemApagar() {
    var data = LocalDate.now();
    var criada = criarTransferencia("PLANEJADA", data).statusCode(201).extract();
    var transferenciaId = UUID.fromString(criada.path("transferenciaId"));
    String versaoOriginal = criada.path("versaoEdicao");
    String versaoAtual =
        alterarComCorpo(
                token,
                transferenciaId,
                comVersao(
                    corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Corrigida", data),
                    versaoOriginal))
            .statusCode(200)
            .extract()
            .path("versaoEdicao");

    given()
        .auth()
        .oauth2(token)
        .queryParam("versaoEdicao", versaoOriginal)
        .when()
        .delete("/api/transferencias/{id}", transferenciaId)
        .then()
        .statusCode(409)
        .body("type", equalTo("urn:economize:problem:VERSAO_DESATUALIZADA"));
    given()
        .auth()
        .oauth2(token)
        .queryParam("inicio", YearMonth.from(data).toString())
        .queryParam("fim", YearMonth.from(data).toString())
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(2));

    given()
        .auth()
        .oauth2(token)
        .queryParam("versaoEdicao", versaoAtual)
        .when()
        .delete("/api/transferencias/{id}", transferenciaId)
        .then()
        .statusCode(204);
  }

  @Test
  void permiteManutencaoComContaInativaEExcluiOsDoisLados() {
    var data = LocalDate.now();
    var criada = criarTransferencia("PLANEJADA", data).statusCode(201).extract();
    var transferenciaId = UUID.fromString(criada.path("transferenciaId"));
    editarConta(token, contaOrigemId, "Conta origem", false);

    String versaoAtual =
        alterarComCorpo(
                token,
                transferenciaId,
                comVersao(
                    corpo(contaOrigemId, contaDestinoId, "PLANEJADA", "Corrigida", data),
                    criada.path("versaoEdicao")))
            .statusCode(200)
            .body("descricao", equalTo("Corrigida"))
            .extract()
            .path("versaoEdicao");
    given()
        .auth()
        .oauth2(token)
        .queryParam("versaoEdicao", versaoAtual)
        .when()
        .delete("/api/transferencias/{id}", transferenciaId)
        .then()
        .statusCode(204);

    var mes = YearMonth.from(data).toString();
    given()
        .auth()
        .oauth2(token)
        .queryParam("inicio", mes)
        .queryParam("fim", mes)
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(0));
  }

  private void assertLadosComMesmoInstante(LocalDate data, UUID id, String instante) {
    var mes = YearMonth.from(data).toString();
    var lados =
        given()
            .auth()
            .oauth2(token)
            .queryParam("inicio", mes)
            .queryParam("fim", mes)
            .when()
            .get("/api/transacoes")
            .then()
            .statusCode(200)
            .body("itens", hasSize(2))
            .extract();
    for (var indice : List.of(0, 1)) {
      assertEquals(id.toString(), lados.path("itens[" + indice + "].operacaoId"));
      assertEquals(instante, lados.path("itens[" + indice + "].efetivadoEm"));
    }
  }

  private ValidatableResponse criarTransferencia(String situacao, LocalDate data) {
    return criarTransferenciaComContas(contaOrigemId, contaDestinoId, situacao, data);
  }

  private ValidatableResponse criarTransferenciaComContas(
      UUID origemId, UUID destinoId, String situacao, LocalDate data) {
    return criarComCorpo(token, corpo(origemId, destinoId, situacao, "Reserva mensal", data));
  }

  private ValidatableResponse criarComCorpo(String tokenUsuario, Map<String, Object> corpo) {
    return given()
        .auth()
        .oauth2(tokenUsuario)
        .contentType("application/json")
        .body(json(corpo))
        .when()
        .post("/api/transferencias")
        .then();
  }

  private ValidatableResponse alterarComCorpo(
      String tokenUsuario, UUID id, Map<String, Object> corpo) {
    return given()
        .auth()
        .oauth2(tokenUsuario)
        .contentType("application/json")
        .body(json(corpo))
        .when()
        .put("/api/transferencias/{id}", id)
        .then();
  }

  private static Map<String, Object> corpo(
      UUID origemId, UUID destinoId, String situacao, String descricao, LocalDate data) {
    var corpo = new LinkedHashMap<String, Object>();
    corpo.put("contaOrigemId", origemId.toString());
    corpo.put("contaDestinoId", destinoId.toString());
    corpo.put("situacao", situacao);
    corpo.put("descricao", descricao);
    corpo.put("observacoes", "Observacao");
    corpo.put("valor", new BigDecimal("125.0000"));
    corpo.put("dataFinanceira", data.toString());
    return corpo;
  }

  private static Map<String, Object> comVersao(Map<String, Object> corpo, Object versao) {
    var comVersao = new LinkedHashMap<>(corpo);
    comVersao.put("versaoEdicao", versao);
    return comVersao;
  }

  private static String json(Object valor) {
    try {
      return JSON.writeValueAsString(valor);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private String cadastrarUsuario(String email) {
    return given()
        .contentType("application/json")
        .body(
            """
            {
              "nome":"Pessoa de teste",
              "email":"%s",
              "senha":"senha segura",
              "timezone":"America/Sao_Paulo"
            }
            """
                .formatted(email))
        .when()
        .post("/api/autenticacao/cadastro")
        .then()
        .statusCode(201)
        .extract()
        .response()
        .getCookie("economize_token");
  }

  private UUID cadastrarConta(String tokenUsuario, String nome, String moeda) {
    return given()
        .auth()
        .oauth2(tokenUsuario)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"%s",
              "moeda":"%s",
              "saldoInicial":0.0000,
              "dataSaldoInicial":"2026-01-01"
            }
            """
                .formatted(nome, moeda))
        .when()
        .post("/api/contas")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getUUID("id");
  }

  private UUID cadastrarCategoria(String tokenUsuario) {
    return given()
        .auth()
        .oauth2(tokenUsuario)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"%s",
              "cor":null,
              "categoriaPaiId":null
            }
            """
                .formatted("Transferências"))
        .when()
        .post("/api/categorias")
        .then()
        .statusCode(201)
        .body("$", not(hasKey("tipo")))
        .extract()
        .jsonPath()
        .getUUID("id");
  }

  private void editarConta(String tokenUsuario, UUID contaId, String nome, boolean ativo) {
    given()
        .auth()
        .oauth2(tokenUsuario)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"%s",
              "moeda":"BRL",
              "saldoInicial":0.0000,
              "dataSaldoInicial":"2026-01-01",
              "ativo":%s
            }
            """
                .formatted(nome, ativo))
        .when()
        .put("/api/contas/{id}", contaId)
        .then()
        .statusCode(200);
  }
}
