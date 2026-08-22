package com.joaovpg.economize.recorrencia;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RecorrenciaResourceTest {
  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;

  private String email;
  private UUID contaId;

  @BeforeEach
  @Transactional
  void prepararDados() {
    email = "recorrencia-" + UUID.randomUUID() + "@example.com";
    var argon2 = Argon2Factory.create();
    var usuario = new Usuario();
    usuario.setNome("Pessoa de recorrencia");
    usuario.setEmail(email);
    usuario.setSenhaHash(argon2.hash(2, 19_456, 1, "senha-segura".toCharArray()));
    usuario.setTimezone(FUSO.getId());
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);

    var conta = new ContaFinanceira();
    conta.setUsuario(usuario);
    conta.setNome("Conta de recorrencia");
    conta.setMoeda("BRL");
    conta.setSaldoInicial(BigDecimal.ZERO);
    conta.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
    contaRepository.persist(conta);
    contaRepository.flush();
    contaId = conta.getId();
  }

  @Test
  void criaRecorrenciaVirtualEConsultaPeloFluxoUnificado() {
    var token = autenticar();
    var resposta = criarRecorrencia(token);
    var segmentoId = UUID.fromString(resposta.extract().path("segmentoId"));

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-01&fim=2026-03")
        .then()
        .statusCode(200)
        .body("itens", hasSize(3))
        .body("itens[0].origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("itens[0].operacaoId", org.hamcrest.Matchers.nullValue())
        .body("itens[0].segmentoRecorrenciaId", equalTo(segmentoId.toString()))
        .body("itens[0].rrule", equalTo("FREQ=MONTHLY;BYMONTHDAY=10;COUNT=3"))
        .body("itens[0].inicioRecorrencia", equalTo("2026-01-10"))
        .body("itens[0].politicaDataOcorrencia", equalTo("PADRAO"));
  }

  @Test
  void excluirEstaEAsProximasNaoApagaMaterializada() {
    var token = autenticar();
    var resposta = criarRecorrencia(token);
    var segmentoId = UUID.fromString(resposta.extract().path("segmentoId"));

    var efetivada =
        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body("{}")
            .when()
            .post(
                "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}/efetivar",
                segmentoId,
                "2026-02-10")
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("situacao", equalTo("EFETIVADA"))
            .body("dataOriginalRecorrencia", equalTo("2026-02-10"));
    var transacaoId = UUID.fromString(efetivada.extract().path("id"));

    given()
        .auth()
        .oauth2(token)
        .queryParam("escopo", "THIS_AND_FUTURE")
        .when()
        .delete(
            "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}", segmentoId, "2026-01-10")
        .then()
        .statusCode(204);

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-01&fim=2026-03")
        .then()
        .statusCode(200)
        .body("itens", hasSize(1))
        .body("itens[0].operacaoId", equalTo(transacaoId.toString()))
        .body("itens[0].situacao", equalTo("EFETIVADA"));

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body("{}")
        .when()
        .post(
            "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}/efetivar",
            segmentoId,
            "2026-02-10")
        .then()
        .statusCode(200)
        .body("id", equalTo(transacaoId.toString()));
  }

  @Test
  void editaOcorrenciaEfetivadaPreservaIdEInstanteDeEfetivacao() {
    var token = autenticar();
    var resposta = criarRecorrencia(token);
    var segmentoId = UUID.fromString(resposta.extract().path("segmentoId"));

    var efetivada =
        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body("{}")
            .when()
            .post(
                "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}/efetivar",
                segmentoId,
                "2026-02-10")
            .then()
            .statusCode(200)
            .extract();
    var transacaoId = efetivada.path("id");
    var efetivadoEm = efetivada.path("efetivadoEm");

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "escopo":"ONLY_THIS",
              "contaId":"%s",
              "tipo":"RECEITA",
              "descricao":"Salario corrigido",
              "valor":1200.00,
              "dataFinanceira":"2026-02-11"
            }
            """
                .formatted(contaId))
        .when()
        .put("/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}", segmentoId, "2026-02-10")
        .then()
        .statusCode(200)
        .body("id", equalTo(transacaoId))
        .body("situacao", equalTo("EFETIVADA"))
        .body("efetivadoEm", equalTo(efetivadoEm));
  }

  @Test
  void cancelamentoImpedeNovaMaterializacaoDeOcorrenciaVirtual() {
    var token = autenticar();
    var resposta = criarRecorrencia(token);
    var segmentoId = UUID.fromString(resposta.extract().path("segmentoId"));

    given()
        .auth()
        .oauth2(token)
        .queryParam("escopo", "THIS_AND_FUTURE")
        .when()
        .delete(
            "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}", segmentoId, "2026-01-10")
        .then()
        .statusCode(204);

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body("{}")
        .when()
        .post(
            "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}/efetivar",
            segmentoId,
            "2026-02-10")
        .then()
        .statusCode(404);
  }

  @Test
  void criaParcelamentoComAjusteDeUltimoDiaEMantemNumeracao() {
    var token = autenticar();
    var resposta =
        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(
                """
                {
                  "tipoGrupo":"PARCELAMENTO",
                  "contaId":"%s",
                  "tipo":"DESPESA",
                  "descricao":"Seguro",
                  "valorPorParcela":100.00,
                  "inicio":"2026-01-31",
                  "frequencia":"MONTHLY",
                  "numeroPrimeiraParcela":1,
                  "quantidadeTotalOriginal":3
                }
                """
                    .formatted(contaId))
            .when()
            .post("/api/recorrencias")
            .then()
            .statusCode(201)
            .body("tipoGrupo", equalTo("PARCELAMENTO"))
            .body("politicaDataOcorrencia", equalTo("AJUSTAR_ULTIMO_DIA_MES"))
            .body("rrule", equalTo("FREQ=MONTHLY;COUNT=3"));
    assertNull(resposta.extract().path("transacaoId"));

    var itens =
        given()
            .auth()
            .oauth2(token)
            .when()
            .get("/api/transacoes?inicio=2026-01&fim=2026-03")
            .then()
            .statusCode(200)
            .extract()
            .path("itens");
    assertEquals(3, ((java.util.List<?>) itens).size());
    var primeiro = ((java.util.Map<?, ?>) ((java.util.List<?>) itens).get(0));
    var segundo = ((java.util.Map<?, ?>) ((java.util.List<?>) itens).get(1));
    var terceiro = ((java.util.Map<?, ?>) ((java.util.List<?>) itens).get(2));
    assertEquals("2026-01-31", primeiro.get("dataFinanceira"));
    assertEquals("2026-02-28", segundo.get("dataFinanceira"));
    assertEquals("2026-03-31", terceiro.get("dataFinanceira"));
    assertEquals(1, primeiro.get("numeroParcela"));
    assertEquals(2, segundo.get("numeroParcela"));
    assertEquals(3, terceiro.get("numeroParcela"));
  }

  @Test
  void editaSomenteEstaMaterializaUmaExcecaoPlanejada() {
    var token = autenticar();
    var resposta = criarRecorrencia(token);
    var segmentoId = UUID.fromString(resposta.extract().path("segmentoId"));

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "escopo":"ONLY_THIS",
              "contaId":"%s",
              "tipo":"RECEITA",
              "descricao":"Salario ajustado",
              "valor":130.00,
              "dataFinanceira":"2026-02-15"
            }
            """
                .formatted(contaId))
        .when()
        .put("/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}", segmentoId, "2026-02-10")
        .then()
        .statusCode(200)
        .body("situacao", equalTo("PLANEJADA"))
        .body("dataOriginalRecorrencia", equalTo("2026-02-10"))
        .body("dataFinanceira", equalTo("2026-02-15"))
        .body("id", notNullValue());

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-01&fim=2026-03")
        .then()
        .statusCode(200)
        .body("itens", hasSize(3))
        .body("itens[1].dataFinanceira", equalTo("2026-02-15"))
        .body("itens[1].valor", equalTo(130.0f))
        .body("itens[1].operacaoId", notNullValue());
  }

  @Test
  void ocorrenciaMovidaDeMesNaoReapareceComoVirtualNoMesOriginal() {
    var token = autenticar();
    var resposta = criarRecorrencia(token);
    var segmentoId = UUID.fromString(resposta.extract().path("segmentoId"));

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "escopo":"ONLY_THIS",
              "contaId":"%s",
              "tipo":"RECEITA",
              "descricao":"Salario antecipado",
              "valor":130.00,
              "dataFinanceira":"2026-01-15"
            }
            """
                .formatted(contaId))
        .when()
        .put("/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}", segmentoId, "2026-02-10")
        .then()
        .statusCode(200);

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-02&fim=2026-02")
        .then()
        .statusCode(200)
        .body("itens", hasSize(0));
  }

  @Test
  void thisAndFutureDivideSegmentoSomenteQuandoOcorrenciaEhVirtual() {
    var token = autenticar();
    var resposta = criarRecorrenciaDiaria(token);
    var segmentoId = UUID.fromString(resposta.extract().path("segmentoId"));

    var novoSegmentoId =
        UUID.fromString(
            given()
                .auth()
                .oauth2(token)
                .contentType("application/json")
                .body(
                    """
                    {
                      "escopo":"THIS_AND_FUTURE",
                      "contaId":"%s",
                      "tipo":"DESPESA",
                      "descricao":"Diaria ajustada",
                      "valor":25.00,
                      "dataFinanceira":"2026-01-12",
                      "frequencia":"DAILY"
                    }
                    """
                        .formatted(contaId))
                .when()
                .put(
                    "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}",
                    segmentoId,
                    "2026-01-12")
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.nullValue())
                .extract()
                .path("segmentoId"));
    assertNotEquals(segmentoId, novoSegmentoId);

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-01&fim=2026-01")
        .then()
        .statusCode(200)
        .body("itens", hasSize(4))
        .body("itens[0].dataFinanceira", equalTo("2026-01-10"))
        .body("itens[1].dataFinanceira", equalTo("2026-01-11"))
        .body("itens[2].dataFinanceira", equalTo("2026-01-12"))
        .body("itens[2].segmentoRecorrenciaId", equalTo(novoSegmentoId.toString()))
        .body("itens[2].valor", equalTo(-25.0f));
  }

  @Test
  void excluiDestaOcorrenciaEmDianteSemApagarAsAnteriores() {
    var token = autenticar();
    var resposta = criarRecorrenciaDiaria(token);
    var segmentoId = UUID.fromString(resposta.extract().path("segmentoId"));

    given()
        .auth()
        .oauth2(token)
        .queryParam("escopo", "THIS_AND_FUTURE")
        .when()
        .delete(
            "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}", segmentoId, "2026-01-12")
        .then()
        .statusCode(204);

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-01&fim=2026-01")
        .then()
        .statusCode(200)
        .body("itens", hasSize(2))
        .body("itens[0].dataFinanceira", equalTo("2026-01-10"))
        .body("itens[1].dataFinanceira", equalTo("2026-01-11"));
  }

  @Test
  void excluirEstaEAsProximasEncerraTodosOsSegmentosFuturosDoGrupo() {
    var token = autenticar();
    var resposta = criarRecorrenciaDiaria(token);
    var segmentoInicial = UUID.fromString(resposta.extract().path("segmentoId"));

    var segmentoIntermediario =
        UUID.fromString(
            given()
                .auth()
                .oauth2(token)
                .contentType("application/json")
                .body(
                    """
                    {
                      "escopo":"THIS_AND_FUTURE",
                      "contaId":"%s",
                      "tipo":"DESPESA",
                      "descricao":"Diaria intermediaria",
                      "valor":25.00,
                      "dataFinanceira":"2026-01-12",
                      "frequencia":"DAILY"
                    }
                    """
                        .formatted(contaId))
                .when()
                .put(
                    "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}",
                    segmentoInicial,
                    "2026-01-12")
                .then()
                .statusCode(200)
                .extract()
                .path("segmentoId"));

    var segmentoFinal =
        UUID.fromString(
            given()
                .auth()
                .oauth2(token)
                .contentType("application/json")
                .body(
                    """
                    {
                      "escopo":"THIS_AND_FUTURE",
                      "contaId":"%s",
                      "tipo":"DESPESA",
                      "descricao":"Diaria final",
                      "valor":30.00,
                      "dataFinanceira":"2026-01-13",
                      "frequencia":"DAILY"
                    }
                    """
                        .formatted(contaId))
                .when()
                .put(
                    "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}",
                    segmentoIntermediario,
                    "2026-01-13")
                .then()
                .statusCode(200)
                .extract()
                .path("segmentoId"));

    given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body("{}")
        .when()
        .post(
            "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}/efetivar",
            segmentoFinal,
            "2026-01-13")
        .then()
        .statusCode(200)
        .body("situacao", equalTo("EFETIVADA"));

    given()
        .auth()
        .oauth2(token)
        .queryParam("escopo", "THIS_AND_FUTURE")
        .when()
        .delete(
            "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}",
            segmentoIntermediario,
            "2026-01-12")
        .then()
        .statusCode(204);

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-01&fim=2026-01")
        .then()
        .statusCode(200)
        .body("itens", hasSize(3))
        .body("itens[0].dataFinanceira", equalTo("2026-01-10"))
        .body("itens[1].dataFinanceira", equalTo("2026-01-11"))
        .body("itens[2].dataFinanceira", equalTo("2026-01-13"))
        .body("itens[2].situacao", equalTo("EFETIVADA"));
  }

  @Test
  void alterarQuantidadeDoParcelamentoCriaNovoGrupoSemRenumerarTrechoAnterior() {
    var token = autenticar();
    var resposta =
        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(
                """
                {
                  "tipoGrupo":"PARCELAMENTO",
                  "contaId":"%s",
                  "tipo":"DESPESA",
                  "descricao":"Curso",
                  "valorPorParcela":80.00,
                  "inicio":"2026-01-10",
                  "frequencia":"MONTHLY",
                  "numeroPrimeiraParcela":1,
                  "quantidadeTotalOriginal":3
                }
                """
                    .formatted(contaId))
            .when()
            .post("/api/recorrencias")
            .then()
            .statusCode(201)
            .extract();
    var grupoAntigo = resposta.path("grupoId");
    var segmentoId = UUID.fromString(resposta.path("segmentoId"));

    var novoGrupo =
        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(
                """
                {
                  "escopo":"THIS_AND_FUTURE",
                  "contaId":"%s",
                  "tipo":"DESPESA",
                  "descricao":"Curso ampliado",
                  "valor":80.00,
                  "dataFinanceira":"2026-02-10",
                  "frequencia":"MONTHLY",
                  "quantidadeTotalOriginal":5
                }
                """
                    .formatted(contaId))
            .when()
            .put(
                "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}",
                segmentoId,
                "2026-02-10")
            .then()
            .statusCode(200)
            .body("id", org.hamcrest.Matchers.nullValue())
            .body("numeroParcela", equalTo(2))
            .extract()
            .path("grupoId");
    assertNotEquals(grupoAntigo, novoGrupo);

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-01&fim=2026-05")
        .then()
        .statusCode(200)
        .body("itens", hasSize(5))
        .body("itens[0].numeroParcela", equalTo(1))
        .body("itens[1].numeroParcela", equalTo(2))
        .body("itens[2].numeroParcela", equalTo(3))
        .body("itens[3].numeroParcela", equalTo(4))
        .body("itens[4].numeroParcela", equalTo(5));
  }

  @Test
  void excluiApenasUmaOcorrenciaVirtualSemRenumerarParcelas() {
    var token = autenticar();
    var resposta =
        given()
            .auth()
            .oauth2(token)
            .contentType("application/json")
            .body(
                """
                {
                  "tipoGrupo":"PARCELAMENTO",
                  "contaId":"%s",
                  "tipo":"DESPESA",
                  "descricao":"Compra",
                  "valorPorParcela":50.00,
                  "inicio":"2026-01-10",
                  "frequencia":"MONTHLY",
                  "numeroPrimeiraParcela":1,
                  "quantidadeTotalOriginal":3
                }
                """
                    .formatted(contaId))
            .when()
            .post("/api/recorrencias")
            .then()
            .statusCode(201)
            .extract();
    var segmentoId = UUID.fromString(resposta.path("segmentoId"));

    given()
        .auth()
        .oauth2(token)
        .when()
        .delete(
            "/api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}", segmentoId, "2026-02-10")
        .then()
        .statusCode(204);

    given()
        .auth()
        .oauth2(token)
        .when()
        .get("/api/transacoes?inicio=2026-01&fim=2026-03")
        .then()
        .statusCode(200)
        .body("itens", hasSize(2))
        .body("itens[0].numeroParcela", equalTo(1))
        .body("itens[1].numeroParcela", equalTo(3));
  }

  private ValidatableResponse criarRecorrencia(String token) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "tipoGrupo":"RECORRENCIA",
              "contaId":"%s",
              "tipo":"RECEITA",
              "descricao":"Salario",
              "valor":1000.00,
              "inicio":"2026-01-10",
              "frequencia":"MONTHLY",
              "diasMes":[10],
              "quantidadeOcorrencias":3
            }
            """
                .formatted(contaId))
        .when()
        .post("/api/recorrencias")
        .then()
        .statusCode(201)
        .body("tipoGrupo", equalTo("RECORRENCIA"))
        .body("rrule", equalTo("FREQ=MONTHLY;BYMONTHDAY=10;COUNT=3"));
  }

  private ValidatableResponse criarRecorrenciaDiaria(String token) {
    return given()
        .auth()
        .oauth2(token)
        .contentType("application/json")
        .body(
            """
            {
              "tipoGrupo":"RECORRENCIA",
              "contaId":"%s",
              "tipo":"DESPESA",
              "descricao":"Diaria",
              "valor":20.00,
              "inicio":"2026-01-10",
              "frequencia":"DAILY",
              "quantidadeOcorrencias":4
            }
            """
                .formatted(contaId))
        .when()
        .post("/api/recorrencias")
        .then()
        .statusCode(201);
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
