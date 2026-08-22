package com.joaovpg.economize.categoria;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2Factory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CategoriaResourceTest {
  @Inject UsuarioRepository usuarioRepository;

  private String email;
  private String outroEmail;

  @BeforeEach
  @Transactional
  void prepararUsuario() {
    email = "categoria-" + UUID.randomUUID() + "@example.com";
    var usuario = new Usuario();
    usuario.setNome("Pessoa de teste");
    usuario.setEmail(email);
    usuario.setSenhaHash(Argon2Factory.create().hash(2, 19_456, 1, "senha-segura".toCharArray()));
    usuario.setTimezone("America/Sao_Paulo");
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);
    outroEmail = "outro-" + UUID.randomUUID() + "@example.com";
    criarUsuario(outroEmail);
  }

  @Test
  void cadastraCategoriaAtivaComDadosNormalizados() {
    given()
        .auth()
        .oauth2(autenticar())
        .contentType("application/json")
        .body(
            """
            {
              "nome":"  Moradia  ",
              "cor":"#33aaff",
              "categoriaPaiId":null
            }
            """)
        .when()
        .post("/api/categorias")
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("nome", equalTo("Moradia"))
        .body("cor", equalTo("#33AAFF"))
        .body("ativo", equalTo(true));
  }

  @Test
  void rejeitaNomeDuplicadoNoMesmoNivelSemDiferenciarCaixa() {
    var token = autenticar();
    cadastrar(token, "Moradia", null);

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {"nome":"moradia","cor":null,"categoriaPaiId":null}
            """)
        .when()
        .post("/api/categorias")
        .then()
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:NOME_CATEGORIA_DUPLICADO"));
  }

  @Test
  void editaCategoriaEListaEstadosEmOrdemAlfabetica() {
    var token = autenticar();
    var moradiaId = cadastrar(token, "Moradia", null);
    var moveisId = cadastrar(token, "Moveis", moradiaId);

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/categorias")
        .then()
        .statusCode(200)
        .body("[1].id", equalTo(moveisId))
        .body("[1].categoriaPaiId", equalTo(moradiaId));

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "nome":" Decoracao ",
              "cor":"#abcdef",
              "categoriaPaiId":null,
              "ativo":false
            }
            """)
        .when()
        .put("/api/categorias/{id}", moveisId)
        .then()
        .statusCode(200)
        .body("nome", equalTo("Decoracao"))
        .body("cor", equalTo("#ABCDEF"))
        .body("categoriaPaiId", equalTo(null))
        .body("ativo", equalTo(false));

    given()
        .auth()
        .oauth2(token)
        .queryParam("ativo", true)
        .when()
        .get("/api/categorias")
        .then()
        .statusCode(200)
        .body("", hasSize(1))
        .body("[0].id", equalTo(moradiaId));

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/categorias")
        .then()
        .statusCode(200)
        .body("nome", equalTo(java.util.List.of("Decoracao", "Moradia")));

    given()
        .auth()
        .oauth2(token)
        .queryParam("ativo", false)
        .when()
        .get("/api/categorias")
        .then()
        .statusCode(200)
        .body("", hasSize(1))
        .body("[0].id", equalTo(moveisId));
  }

  @Test
  void rejeitaCicloIndiretoNaHierarquia() {
    var token = autenticar();
    var moradiaId = cadastrar(token, "Moradia", null);
    var moveisId = cadastrar(token, "Moveis", moradiaId);

    editar(token, moradiaId, "Moradia", moveisId, true)
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:HIERARQUIA_CATEGORIA_CICLICA"));
  }

  @Test
  void exigeInativacaoDeBaixoParaCimaEAtivacaoDeCimaParaBaixo() {
    var token = autenticar();
    var moradiaId = cadastrar(token, "Moradia", null);
    var moveisId = cadastrar(token, "Moveis", moradiaId);

    editar(token, moradiaId, "Moradia", null, false)
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:CATEGORIA_POSSUI_DESCENDENTE_ATIVA"));
    editar(token, moveisId, "Moveis", moradiaId, false).statusCode(200);
    editar(token, moradiaId, "Moradia", null, false).statusCode(200);
    editar(token, moveisId, "Moveis", moradiaId, true)
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:CATEGORIA_POSSUI_ANCESTRAL_INATIVA"));
  }

  @Test
  void permiteAtivarCategoriaAoMoveLaParaUmPaiAtivo() {
    var token = autenticar();
    var paiInativoId = cadastrar(token, "Arquivadas", null);
    var paiAtivoId = cadastrar(token, "Despesas", null);
    var filhaId = cadastrar(token, "Moradia", paiInativoId);
    editar(token, filhaId, "Moradia", paiInativoId, false).statusCode(200);
    editar(token, paiInativoId, "Arquivadas", null, false).statusCode(200);

    editar(token, filhaId, "Moradia", paiAtivoId, true)
        .statusCode(200)
        .body("categoriaPaiId", equalTo(paiAtivoId))
        .body("ativo", equalTo(true));
  }

  @Test
  void ocultaCategoriaDeOutroUsuario() {
    var categoriaId = cadastrar(autenticar(), "Moradia", null);
    email = outroEmail;

    editar(autenticar(), categoriaId, "Invadida", null, true)
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
  }

  private String cadastrar(String token, String nome, String paiId) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {"nome":"%s","cor":null,"categoriaPaiId":%s}
            """
                .formatted(nome, paiId == null ? "null" : "\"" + paiId + "\""))
        .when()
        .post("/api/categorias")
        .then()
        .statusCode(201)
        .extract()
        .path("id");
  }

  private io.restassured.response.ValidatableResponse editar(
      String token, String categoriaId, String nome, String paiId, boolean ativo) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {"nome":"%s","cor":null,"categoriaPaiId":%s,"ativo":%s}
            """
                .formatted(nome, paiId == null ? "null" : "\"" + paiId + "\"", ativo))
        .when()
        .put("/api/categorias/{id}", categoriaId)
        .then();
  }

  void criarUsuario(String emailUsuario) {
    var usuario = new Usuario();
    usuario.setNome("Outra pessoa");
    usuario.setEmail(emailUsuario);
    usuario.setSenhaHash(Argon2Factory.create().hash(2, 19_456, 1, "senha-segura".toCharArray()));
    usuario.setTimezone("America/Sao_Paulo");
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);
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
}
