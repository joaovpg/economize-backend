package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2Factory;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Versão de uma transação simples destacada de série, criada por ESTA_E_AS_PROXIMAS com repetição
 * nula: inclui a revisão do grupo de origem, de modo que escritas na série tornam a versão lida
 * desatualizada.
 */
@QuarkusTest
class VersaoOcorrenciaDestacadaResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");
  private static final String INICIO = "2026-09-10";
  private static final String VERSAO_DESATUALIZADA = "urn:economize:problem:VERSAO_DESATUALIZADA";
  private static final String MENSAL_DIA_10 =
      "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
          + "\"termino\":{\"tipo\":\"INDEFINIDO\"}}";

  /**
   * Repetição lida depois de ESTA_E_AS_PROXIMAS em novembro: o segmento passa a terminar em 09/11.
   */
  private static final String REPETICAO_APOS_CORTE =
      "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
          + "\"termino\":{\"tipo\":\"EM_DATA\",\"data\":\"2026-11-09\"}}";

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;

  private String email;
  private UUID contaId;

  @BeforeEach
  @Transactional
  void prepararDados() {
    email = "destacada-" + UUID.randomUUID() + "@example.com";
    var usuario = new Usuario();
    usuario.setNome("Pessoa destacada");
    usuario.setEmail(email);
    usuario.setSenhaHash(Argon2Factory.create().hash(2, 19_456, 1, "senha-segura".toCharArray()));
    usuario.setTimezone(FUSO.getId());
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);

    var conta = new ContaFinanceira();
    conta.setUsuario(usuario);
    conta.setNome("Conta destacada");
    conta.setMoeda("BRL");
    conta.setSaldoInicial(BigDecimal.ZERO);
    conta.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
    contaRepository.persist(conta);
    contaRepository.flush();
    contaId = conta.getId();
  }

  @Test
  void escritaNaSerieInvalidaVersaoDeDestacadaLidaAntes() {
    var token = autenticar();
    criar(token, corpo(INICIO, MENSAL_DIA_10)).statusCode(201);
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");
    var conversao =
        alterar(
                token,
                (String) novembro.get("chaveEdicao"),
                alteracao(
                    (String) novembro.get("versaoEdicao"),
                    "ESTA_E_AS_PROXIMAS",
                    "null",
                    "PLANEJADA",
                    "2026-11-10",
                    "100"))
            .statusCode(200)
            .extract();
    String chaveDestacada = conversao.path("chaveEdicao");

    var destacadaLida = itemDe(token, "2026-11", "2026-11", "2026-11-10");
    String versaoLida = (String) destacadaLida.get("versaoEdicao");
    assertEquals(chaveDestacada, destacadaLida.get("chaveEdicao"));

    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                "SOMENTE_ESTA",
                REPETICAO_APOS_CORTE,
                "PLANEJADA",
                "2026-10-10",
                "90"))
        .statusCode(200);

    alterar(
            token,
            chaveDestacada,
            alteracao(versaoLida, null, "null", "PLANEJADA", "2026-11-10", "150"))
        .statusCode(409)
        .body("type", equalTo(VERSAO_DESATUALIZADA));

    var versaoAtual = itemDe(token, "2026-11", "2026-11", "2026-11-10").get("versaoEdicao");
    assertNotEquals(versaoLida, versaoAtual);
    alterar(
            token,
            chaveDestacada,
            alteracao(String.valueOf(versaoAtual), null, "null", "PLANEJADA", "2026-11-10", "150"))
        .statusCode(200);
  }

  @Test
  void exclusaoDeDestacadaComVersaoDeAntesDaEscritaNaSerieRespondeConflito() {
    var token = autenticar();
    criar(token, corpo(INICIO, MENSAL_DIA_10)).statusCode(201);
    var novembro = itemDe(token, "2026-11", "2026-11", "2026-11-10");
    alterar(
            token,
            (String) novembro.get("chaveEdicao"),
            alteracao(
                (String) novembro.get("versaoEdicao"),
                "ESTA_E_AS_PROXIMAS",
                "null",
                "PLANEJADA",
                "2026-11-10",
                "100"))
        .statusCode(200);
    var destacadaLida = itemDe(token, "2026-11", "2026-11", "2026-11-10");
    String versaoLida = (String) destacadaLida.get("versaoEdicao");

    var outubro = itemDe(token, "2026-10", "2026-10", "2026-10-10");
    alterar(
            token,
            (String) outubro.get("chaveEdicao"),
            alteracao(
                (String) outubro.get("versaoEdicao"),
                "SOMENTE_ESTA",
                REPETICAO_APOS_CORTE,
                "PLANEJADA",
                "2026-10-10",
                "90"))
        .statusCode(200);

    excluir(token, (String) destacadaLida.get("chaveEdicao"), versaoLida)
        .statusCode(409)
        .body("type", equalTo(VERSAO_DESATUALIZADA));
    consultar(token, "2026-11", "2026-11").statusCode(200).body("itens", hasSize(1));
  }

  private ValidatableResponse criar(String token, String corpo) {
    return given()
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
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(corpo)
        .when()
        .put("/api/transacoes/{chave}", chave)
        .then();
  }

  private ValidatableResponse excluir(String token, String chave, String versao) {
    return given()
        .auth()
        .oauth2(token)
        .queryParam("versaoEdicao", versao)
        .when()
        .delete("/api/transacoes/{chave}", chave)
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

  private String corpo(String dataFinanceira, String repeticao) {
    return """
    {"tipo":"DESPESA","contaId":"%s","categoriaId":null,"descricao":"Aluguel",
     "observacoes":null,"valor":100.00,"dataFinanceira":"%s","situacao":"PLANEJADA",
     "repeticao":%s}
    """
        .formatted(contaId, dataFinanceira, repeticao);
  }

  private String alteracao(
      String versao,
      String escopo,
      String repeticao,
      String situacao,
      String dataFinanceira,
      String valor) {
    var escopoJson = escopo == null ? "" : "\"escopo\":\"" + escopo + "\",";
    return "{"
        + escopoJson
        + "\"repeticao\":"
        + repeticao
        + ",\"contaId\":\""
        + contaId
        + "\",\"categoriaId\":null,\"situacao\":\""
        + situacao
        + "\",\"tipo\":\"DESPESA\",\"descricao\":\"Aluguel\",\"observacoes\":null,"
        + "\"valor\":"
        + valor
        + ",\"dataFinanceira\":\""
        + dataFinanceira
        + "\",\"versaoEdicao\":\""
        + versao
        + "\"}";
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
