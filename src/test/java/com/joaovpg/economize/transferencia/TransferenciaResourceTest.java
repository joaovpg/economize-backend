package com.joaovpg.economize.transferencia;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransferenciaResourceTest {
  private String token;
  private UUID contaOrigemId;
  private UUID contaDestinoId;
  private UUID categoriaId;

  @BeforeEach
  void preparar() {
    token = cadastrarUsuario("transferencia-" + UUID.randomUUID() + "@example.com");
    contaOrigemId = cadastrarConta(token, "Conta origem");
    contaDestinoId = cadastrarConta(token, "Conta destino");
    categoriaId = cadastrarCategoria(token, "Transferências");
  }

  @Test
  void criaTransferenciaEExibeLadosNaLinhaDoTempo() {
    var data = LocalDate.now();
    var transferenciaId =
        criarTransferencia("PLANEJADA", data)
            .statusCode(201)
            .body("id", notNullValue())
            .body("contaOrigemId", equalTo(contaOrigemId.toString()))
            .body("contaDestinoId", equalTo(contaDestinoId.toString()))
            .body("situacao", equalTo("PLANEJADA"))
            .body("efetivadoEm", nullValue())
            .extract()
            .jsonPath()
            .getUUID("id");

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
        .body("itens[0]", not(hasKey("tipo")))
        .body("itens[0].valor", equalTo(new BigDecimal("-125.0000")))
        .body("itens[0].contaId", equalTo(contaOrigemId.toString()))
        .body("itens[0].contaContraparteId", equalTo(contaDestinoId.toString()))
        .body("itens[0].categoriaId", nullValue())
        .body("itens[1].operacaoId", equalTo(transferenciaId.toString()))
        .body("itens[1]", not(hasKey("tipo")))
        .body("itens[1].valor", equalTo(new BigDecimal("125.0000")))
        .body("itens[1].contaId", equalTo(contaDestinoId.toString()))
        .body("itens[1].contaContraparteId", equalTo(contaOrigemId.toString()))
        .body("itens[1].categoriaId", nullValue())
        .body("saldoAbertura", equalTo(new BigDecimal("0.0000")));
  }

  @Test
  void filtraCadaLadoPorContaEExcluiTransferenciasAoFiltrarCategoria() {
    var data = LocalDate.now();
    criarTransferencia("PLANEJADA", data).statusCode(201);
    var mes = YearMonth.from(data).toString();

    given()
        .auth()
        .oauth2(token)
        .queryParam("inicio", mes)
        .queryParam("fim", mes)
        .queryParam("contaId", contaOrigemId)
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(1))
        .body("itens[0].valor", equalTo(-125.0F))
        .body("itens[0].contaContraparteId", equalTo(contaDestinoId.toString()));

    given()
        .auth()
        .oauth2(token)
        .queryParam("inicio", mes)
        .queryParam("fim", mes)
        .queryParam("categoriaId", categoriaId)
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(0));
  }

  @Test
  void incluiLadosAnterioresNoSaldoDeAbertura() {
    var data = LocalDate.now();
    criarTransferencia("PLANEJADA", data).statusCode(201);
    var proximoMes = YearMonth.from(data).plusMonths(1).toString();

    given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .queryParam("inicio", proximoMes)
        .queryParam("fim", proximoMes)
        .queryParam("contaId", contaOrigemId)
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("itens", hasSize(0))
        .body("saldoAbertura", equalTo(new BigDecimal("-125.0000")));

    given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .queryParam("inicio", proximoMes)
        .queryParam("fim", proximoMes)
        .when()
        .get("/api/transacoes")
        .then()
        .statusCode(200)
        .body("saldoAbertura", equalTo(new BigDecimal("0.0000")));
  }

  @Test
  void efetivaCorrigeReplanejaEEfetivaNovamente() {
    var data = LocalDate.now();
    var transferenciaId =
        criarTransferencia("PLANEJADA", data).statusCode(201).extract().jsonPath().getUUID("id");

    var primeiroInstante =
        alterarTransferencia(transferenciaId, "EFETIVADA", data, "Efetivada")
            .statusCode(200)
            .body("situacao", equalTo("EFETIVADA"))
            .body("efetivadoEm", notNullValue())
            .extract()
            .path("efetivadoEm");
    alterarTransferencia(transferenciaId, "EFETIVADA", data, "Corrigida")
        .statusCode(200)
        .body("efetivadoEm", equalTo(primeiroInstante));
    alterarTransferencia(transferenciaId, "PLANEJADA", data, "Replanejada")
        .statusCode(200)
        .body("efetivadoEm", nullValue());
    var segundoInstante =
        alterarTransferencia(transferenciaId, "EFETIVADA", data, "Efetivada novamente")
            .statusCode(200)
            .extract()
            .path("efetivadoEm");

    assertNotEquals(primeiroInstante, segundoInstante);
  }

  @Test
  void permiteManutencaoComContaInativaEExcluiOsDoisLados() {
    var data = LocalDate.now();
    var transferenciaId =
        criarTransferencia("PLANEJADA", data).statusCode(201).extract().jsonPath().getUUID("id");
    editarConta(token, contaOrigemId, "Conta origem", false);

    alterarTransferencia(transferenciaId, "PLANEJADA", data, "Corrigida")
        .statusCode(200)
        .body("descricao", equalTo("Corrigida"));
    given()
        .auth()
        .oauth2(token)
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
        criarTransferencia("PLANEJADA", data).statusCode(201).extract().jsonPath().getUUID("id");
    var outroToken = cadastrarUsuario("outra-transferencia-" + UUID.randomUUID() + "@example.com");
    given()
        .auth()
        .oauth2(outroToken)
        .when()
        .delete("/api/transferencias/{id}", transferenciaId)
        .then()
        .statusCode(404);
  }

  private ValidatableResponse criarTransferencia(String situacao, LocalDate data) {
    return criarTransferenciaComContas(contaOrigemId, contaDestinoId, situacao, data);
  }

  private ValidatableResponse criarTransferenciaComContas(
      UUID origemId, UUID destinoId, String situacao, LocalDate data) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "contaOrigemId":"%s",
              "contaDestinoId":"%s",
              "situacao":"%s",
              "descricao":"Reserva mensal",
              "observacoes":"Observacao",
              "valor":125.0000,
              "dataFinanceira":"%s"
            }
            """
                .formatted(origemId, destinoId, situacao, data))
        .when()
        .post("/api/transferencias")
        .then();
  }

  private ValidatableResponse alterarTransferencia(
      UUID id, String situacao, LocalDate data, String descricao) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "contaOrigemId":"%s",
              "contaDestinoId":"%s",
              "situacao":"%s",
              "descricao":"%s",
              "observacoes":null,
              "valor":125.0000,
              "dataFinanceira":"%s"
            }
            """
                .formatted(contaOrigemId, contaDestinoId, situacao, descricao, data))
        .when()
        .put("/api/transferencias/{id}", id)
        .then();
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
        .path("token");
  }

  private UUID cadastrarConta(String tokenUsuario, String nome) {
    return given()
        .auth()
        .oauth2(tokenUsuario)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"%s",
              "moeda":"BRL",
              "saldoInicial":0.0000,
              "dataSaldoInicial":"2026-01-01"
            }
            """
                .formatted(nome))
        .when()
        .post("/api/contas")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getUUID("id");
  }

  private UUID cadastrarCategoria(String tokenUsuario, String nome) {
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
                .formatted(nome))
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
