package com.joaovpg.economize.conta;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2Factory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContaFinanceiraResourceTest {
  @Inject UsuarioRepository usuarioRepository;

  private String email;
  private String outroEmail;

  @BeforeEach
  @Transactional
  void prepararUsuario() {
    email = "conta-" + UUID.randomUUID() + "@example.com";
    var usuario = new Usuario();
    usuario.setNome("Pessoa de teste");
    usuario.setEmail(email);
    usuario.setSenhaHash(Argon2Factory.create().hash(2, 19_456, 1, "senha-segura".toCharArray()));
    usuario.setTimezone("America/Sao_Paulo");
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);
    outroEmail = "outra-conta-" + UUID.randomUUID() + "@example.com";
    criarUsuario(outroEmail);
  }

  @Test
  void cadastraContaAtivaComDadosNormalizados() {
    var resposta =
        given()
            .auth()
            .oauth2(autenticar())
            .contentType("application/json")
            .body(
                """
                {
                  "nome":"  Conta principal  ",
                  "moeda":"BRL",
                  "saldoInicial":-50.2500,
                  "dataSaldoInicial":"2026-01-01"
                }
                """)
            .when()
            .post("/api/contas")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("nome", equalTo("Conta principal"))
            .body("moeda", equalTo("BRL"))
            .body("dataSaldoInicial", equalTo("2026-01-01"))
            .body("ativo", equalTo(true))
            .extract()
            .response();
    assertEquals(
        0,
        new java.math.BigDecimal(resposta.jsonPath().getString("saldoInicial"))
            .compareTo(new java.math.BigDecimal("-50.2500")));
  }

  @Test
  void listaContasEmOrdemDeterministicaSemPaginacao() {
    var token = autenticar();
    cadastrar(token, "zeta");
    cadastrar(token, "Alfa");

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/contas")
        .then()
        .statusCode(200)
        .body("nome", equalTo(java.util.List.of("Alfa", "zeta")));
  }

  @Test
  void editaTodosOsDadosEInativaAConta() {
    var token = autenticar();
    var contaId = cadastrar(token, "Conta principal");

    var resposta =
        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(
                """
                {
                  "nome":"  Reserva  ",
                  "moeda":"BRL",
                  "saldoInicial":100.5000,
                  "dataSaldoInicial":"2026-02-01",
                  "ativo":false
                }
                """)
            .when()
            .put("/api/contas/{id}", contaId)
            .then()
            .statusCode(200)
            .body("nome", equalTo("Reserva"))
            .body("dataSaldoInicial", equalTo("2026-02-01"))
            .body("ativo", equalTo(false))
            .extract()
            .response();
    assertEquals(
        0,
        new java.math.BigDecimal(resposta.jsonPath().getString("saldoInicial"))
            .compareTo(new java.math.BigDecimal("100.5000")));
  }

  @Test
  void filtraListagemPorAtivo() {
    var token = autenticar();
    cadastrar(token, "Ativa");
    var inativaId = cadastrar(token, "Inativa");
    editar(token, inativaId, "Inativa", false).statusCode(200);

    given()
        .auth()
        .oauth2(token)
        .queryParam("ativo", false)
        .when()
        .get("/api/contas")
        .then()
        .statusCode(200)
        .body("nome", equalTo(java.util.List.of("Inativa")));
  }

  @Test
  void reativaContaComAtivoVerdadeiro() {
    var token = autenticar();
    var contaId = cadastrar(token, "Conta reativada");
    editar(token, contaId, "Conta reativada", false).statusCode(200);

    editar(token, contaId, "Conta reativada", true).statusCode(200).body("ativo", equalTo(true));
  }

  @Test
  void rejeitaEdicaoSemAtivo() {
    var token = autenticar();
    var contaId = cadastrar(token, "Conta sem ativo");

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"Conta sem ativo",
              "moeda":"BRL",
              "saldoInicial":0,
              "dataSaldoInicial":"2026-01-01"
            }
            """)
        .when()
        .put("/api/contas/{id}", contaId)
        .then()
        .statusCode(400)
        .body("type", equalTo("urn:economize:problem:DADOS_INVALIDOS"))
        .body("errors.find { it.field == 'ativo' }.detail", notNullValue());
  }

  @Test
  void rejeitaNomeDuplicadoInclusiveComContaInativa() {
    var token = autenticar();
    var contaId = cadastrar(token, "Reserva");
    editar(token, contaId, "Reserva", false).statusCode(200);

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"reserva",
              "moeda":"BRL",
              "saldoInicial":0,
              "dataSaldoInicial":"2026-01-01"
            }
            """)
        .when()
        .post("/api/contas")
        .then()
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:NOME_CONTA_DUPLICADO"));
  }

  @Test
  void rejeitaMoedaForaDoMvpEExcessoDeCasasDecimais() {
    var token = autenticar();

    cadastrarInvalida(token, "brl", "0")
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:MOEDA_CONTA_INVALIDA"));
    cadastrarInvalida(token, "BRL", "0.00001")
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:SALDO_INICIAL_INVALIDO"));
  }

  @Test
  void rejeitaDataDoSaldoInicialFuturaNoFusoDoUsuario() {
    var dataFutura = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);

    given()
        .auth()
        .oauth2(autenticar())
        .contentType("application/json")
        .body(
            """
            {
              "nome":"Futura",
              "moeda":"BRL",
              "saldoInicial":0,
              "dataSaldoInicial":"%s"
            }
            """
                .formatted(dataFutura))
        .when()
        .post("/api/contas")
        .then()
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_SALDO_INICIAL_INVALIDA"));
  }

  @Test
  void isolaEdicaoEListagemPorUsuario() {
    var contaId = cadastrar(autenticar(), "Privada");
    email = outroEmail;
    var tokenOutroUsuario = autenticar();

    editar(tokenOutroUsuario, contaId, "Invadida", true)
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    given()
        .auth()
        .oauth2(tokenOutroUsuario)
        .when()
        .get("/api/contas")
        .then()
        .statusCode(200)
        .body("", hasSize(0));
  }

  private io.restassured.response.ValidatableResponse cadastrarInvalida(
      String token, String moeda, String saldoInicial) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"Conta invalida",
              "moeda":"%s",
              "saldoInicial":%s,
              "dataSaldoInicial":"2026-01-01"
            }
            """
                .formatted(moeda, saldoInicial))
        .when()
        .post("/api/contas")
        .then();
  }

  private io.restassured.response.ValidatableResponse editar(
      String token, String contaId, String nome, boolean ativo) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"%s",
              "moeda":"BRL",
              "saldoInicial":0,
              "dataSaldoInicial":"2026-01-01",
              "ativo":%s
            }
            """
                .formatted(nome, ativo))
        .when()
        .put("/api/contas/{id}", contaId)
        .then();
  }

  private String cadastrar(String token, String nome) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "nome":"%s",
              "moeda":"BRL",
              "saldoInicial":0,
              "dataSaldoInicial":"2026-01-01"
            }
            """
                .formatted(nome))
        .when()
        .post("/api/contas")
        .then()
        .statusCode(201)
        .extract()
        .path("id");
  }

  private String autenticar() {
    return given()
        .contentType("application/json")
        .body(
            """
            {"email":"%s","senha":"senha-segura"}
            """
                .formatted(email))
        .when()
        .post("/api/autenticacao/login")
        .then()
        .statusCode(200)
        .extract()
        .response()
        .getCookie("economize_token");
  }

  private void criarUsuario(String emailUsuario) {
    var usuario = new Usuario();
    usuario.setNome("Outra pessoa");
    usuario.setEmail(emailUsuario);
    usuario.setSenhaHash(Argon2Factory.create().hash(2, 19_456, 1, "senha-segura".toCharArray()));
    usuario.setTimezone("America/Sao_Paulo");
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);
  }
}
