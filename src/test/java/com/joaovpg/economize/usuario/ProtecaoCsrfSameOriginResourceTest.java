package com.joaovpg.economize.usuario;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProtecaoCsrfSameOriginResourceTest {

  @Test
  void permiteOperacaoMutavelSameOriginSemHeaderCsrf() {
    var sessao = criarSessao();

    given()
        .cookie("economize_token", sessao.token())
        .header("Sec-Fetch-Site", "same-origin")
        .contentType("application/json")
        .body(
            """
            {
              "nome":"Gastos essenciais",
              "cor":"#FFFFFF"
            }
            """)
        .when()
        .post("/api/categorias")
        .then()
        .statusCode(201)
        .body("nome", equalTo("Gastos essenciais"));
  }

  @Test
  void continuaExigindoCsrfEmOperacaoCrossSite() {
    var sessao = criarSessao();

    given()
        .cookie("economize_token", sessao.token())
        .header("Sec-Fetch-Site", "cross-site")
        .contentType("application/json")
        .body(
            """
            {
              "nome":"Gastos essenciais",
              "cor":"#FFFFFF"
            }
            """)
        .when()
        .post("/api/categorias")
        .then()
        .statusCode(403)
        .body("type", equalTo("urn:economize:problem:CSRF_TOKEN_INVALIDO"));
  }

  private Sessao criarSessao() {
    var email = "csrf-same-origin-" + UUID.randomUUID() + "@example.com";
    var response =
        given()
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
            .response();

    return new Sessao(response.getCookie("economize_token"));
  }

  private record Sessao(String token) {}
}
