package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2Factory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Garante que as rotas antigas de escrita de recorrência e os escopos em inglês foram retirados e
 * que o OpenAPI publica o contrato unificado.
 */
@QuarkusTest
class ContratoEscritaUnificadaResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;

  private String email;

  @BeforeEach
  @Transactional
  void prepararDados() {
    email = "contrato-" + UUID.randomUUID() + "@example.com";
    var usuario = new Usuario();
    usuario.setNome("Pessoa de contrato");
    usuario.setEmail(email);
    usuario.setSenhaHash(Argon2Factory.create().hash(2, 19_456, 1, "senha-segura".toCharArray()));
    usuario.setTimezone(FUSO.getId());
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);

    var conta = new ContaFinanceira();
    conta.setUsuario(usuario);
    conta.setNome("Conta de contrato");
    conta.setMoeda("BRL");
    conta.setSaldoInicial(BigDecimal.ZERO);
    conta.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
    contaRepository.persist(conta);
    contaRepository.flush();
  }

  @Test
  void rotasAntigasDeEscritaDeRecorrenciaNaoExistemMais() {
    var token = autenticar();
    var segmentoId = UUID.randomUUID();

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/recorrencias")
        .then()
        .statusCode(anyOf(is(404), is(405)));
    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body("{}")
        .when()
        .put("/api/recorrencias/{s}/ocorrencias/{d}", segmentoId, "2026-01-10")
        .then()
        .statusCode(anyOf(is(404), is(405)));
    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/recorrencias/{s}/ocorrencias/{d}/efetivar", segmentoId, "2026-01-10")
        .then()
        .statusCode(anyOf(is(404), is(405)));
    given()
        .auth()
        .oauth2(token)
        .queryParam("escopo", "ONLY_THIS")
        .when()
        .delete("/api/recorrencias/{s}/ocorrencias/{d}", segmentoId, "2026-01-10")
        .then()
        .statusCode(anyOf(is(404), is(405)));
  }

  @Test
  void openApiPublicaExclusaoUnificadaComEscoposEmPortugues() {
    var documento = openApi();

    var paths = mapa(documento.get("paths"));
    assertFalse(
        paths.keySet().stream().anyMatch(caminho -> caminho.startsWith("/api/recorrencias")));
    assertFalse(paths.containsKey("/api/transacoes/{id}"));
    assertTrue(paths.containsKey("/api/transacoes/{chaveEdicao}"));

    var exclusao = mapa(mapa(paths.get("/api/transacoes/{chaveEdicao}")).get("delete"));
    assertEquals("excluirTransacao", exclusao.get("operationId"));
    assertTrue(exclusao.containsKey("responses"));
    var respostas = mapa(exclusao.get("responses"));
    assertTrue(respostas.containsKey("204"));
    assertTrue(respostas.containsKey("404"));
    assertTrue(respostas.containsKey("422"));

    var escopo = parametroEscopo(exclusao, documento);
    assertEquals(List.of("SOMENTE_ESTA", "ESTA_E_AS_PROXIMAS"), escopo);

    var schemas = mapa(mapa(documento.get("components")).get("schemas"));
    assertFalse(schemas.containsKey("EscopoOcorrencia"));
  }

  @Test
  void openApiNaoContemEscoposOuRotasAntigas() {
    var texto = openApiTexto();

    assertFalse(texto.contains("ONLY_THIS"));
    assertFalse(texto.contains("THIS_AND_FUTURE"));
    assertFalse(texto.contains("\"SINGLE\""));
    assertFalse(texto.contains("\"FUTURE\""));
    assertFalse(texto.contains("/api/recorrencias"));
    assertTrue(texto.contains("SOMENTE_ESTA"));
    assertTrue(texto.contains("ESTA_E_AS_PROXIMAS"));
  }

  private List<String> parametroEscopo(
      Map<String, Object> operacao, Map<String, Object> documento) {
    var parametros = (List<?>) operacao.get("parameters");
    assertNotNull(parametros);
    for (var item : parametros) {
      var parametro = mapa(item);
      if ("escopo".equals(parametro.get("name"))) {
        var schema = mapa(parametro.get("schema"));
        if (schema.containsKey("$ref")) {
          var nome = ((String) schema.get("$ref")).replace("#/components/schemas/", "");
          schema = mapa(mapa(mapa(documento.get("components")).get("schemas")).get(nome));
        }
        return ((List<?>) schema.get("enum")).stream().map(String::valueOf).toList();
      }
    }
    throw new AssertionError("parametro escopo ausente na exclusao");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapa(Object valor) {
    assertNotNull(valor);
    return (Map<String, Object>) valor;
  }

  private Map<String, Object> openApi() {
    return mapa(
        given()
            .accept("application/json")
            .when()
            .get("/q/openapi?format=JSON")
            .then()
            .statusCode(200)
            .extract()
            .as(Map.class));
  }

  private String openApiTexto() {
    return given().when().get("/q/openapi?format=JSON").then().statusCode(200).extract().asString();
  }

  private String autenticar() {
    return given()
        .contentType("application/json")
        .body("{\"email\":\"%s\",\"senha\":\"senha-segura\"}".formatted(email))
        .when()
        .post("/api/autenticacao/login")
        .then()
        .statusCode(200)
        .extract()
        .response()
        .getCookie("economize_token");
  }
}
