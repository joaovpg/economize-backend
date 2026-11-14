package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SupressaoRecorrencia;
import com.joaovpg.economize.recorrencia.SupressaoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.domain.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.domain.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.domain.TipoGrupoRecorrencia;
import com.joaovpg.economize.transacao.domain.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConsultaSeriesResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;
  @Inject GrupoRecorrenciaRepository grupoRepository;
  @Inject SegmentoRecorrenciaRepository segmentoRepository;
  @Inject SupressaoRecorrenciaRepository supressaoRepository;
  @Inject TransacaoRepository transacaoRepository;

  private String email;
  private String emailOutroUsuario;
  private UUID usuarioId;
  private UUID contaId;

  @BeforeEach
  void prepararUsuarios() {
    email = "series-" + UUID.randomUUID() + "@example.com";
    emailOutroUsuario = "series-outra-" + UUID.randomUUID() + "@example.com";
    var argon2 = Argon2Factory.create();
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              var usuario = novoUsuario(email, "Pessoa de teste", argon2, "senha-segura");
              var conta = new ContaFinanceira();
              conta.setUsuario(usuario);
              conta.setNome("Conta principal");
              conta.setMoeda("BRL");
              conta.setSaldoInicial(new BigDecimal("100.0000"));
              conta.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
              contaRepository.persist(conta);
              contaRepository.flush();
              usuarioId = usuario.getId();
              contaId = conta.getId();
              novoUsuario(emailOutroUsuario, "Outra pessoa", argon2, "outra-senha");
            });
  }

  @Test
  void consultaSerieIndefinidaEmPeriodosProximosESemMaterializarOcorrencias() {
    var segmentoId =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  var segmento =
                      persistirSegmento(
                          "Aluguel",
                          TipoGrupoRecorrencia.RECORRENCIA,
                          "FREQ=MONTHLY;BYMONTHDAY=10",
                          LocalDate.of(2026, 1, 10),
                          PoliticaDataOcorrencia.PADRAO,
                          null,
                          null);
                  var supressao = new SupressaoRecorrencia();
                  supressao.setUsuario(segmento.getUsuario());
                  supressao.setGrupo(segmento.getGrupo());
                  supressao.setSegmento(segmento);
                  supressao.setIdentificadorRecorrencia(LocalDate.of(2026, 2, 10));
                  supressaoRepository.persist(supressao);
                  return segmento.getId();
                });

    requisicao(autenticar(), "2026-02", "2026-02")
        .statusCode(200)
        .body("itens", empty())
        .body("saldoAbertura", equalTo(new BigDecimal("50.0000")));

    requisicao(autenticar(), "2026-03", "2026-03")
        .statusCode(200)
        .body("saldoAbertura", equalTo(new BigDecimal("50.0000")))
        .body("itens", hasSize(1))
        .body("itens[0].origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("itens[0].virtual", equalTo(true))
        .body("itens[0].protegida", equalTo(false))
        .body("itens[0].situacao", equalTo("PLANEJADA"))
        .body("itens[0].operacaoId", nullValue())
        .body("itens[0].valor", equalTo(new BigDecimal("-50.0000")))
        .body("itens[0].dataFinanceira", equalTo("2026-03-10"))
        .body("itens[0].chaveEdicao", notNullValue())
        .body("itens[0].versaoEdicao", notNullValue())
        .body("itens[0].tipo", equalTo("DESPESA"))
        .body("itens[0].serie.segmentoId", equalTo(segmentoId.toString()))
        .body("itens[0].serie.inicioSegmento", equalTo("2026-01-10"))
        .body("itens[0].serie.dataOriginal", equalTo("2026-03-10"))
        .body("itens[0].serie.numeroParcela", nullValue())
        .body("itens[0].serie.totalParcelas", nullValue())
        .body("itens[0].repeticao.frequencia", equalTo("MENSAL"))
        .body("itens[0].repeticao.intervalo", equalTo(1))
        .body("itens[0].repeticao.diasSemana", empty())
        .body("itens[0].repeticao.diasMes", contains(10))
        .body("itens[0].repeticao.politicaData", equalTo("PADRAO"))
        .body("itens[0].repeticao.termino.tipo", equalTo("INDEFINIDO"))
        .body("itens[0].repeticao.termino", not(hasKey("data")))
        .body("itens[0].repeticao.termino", not(hasKey("quantidade")))
        .body("itens[0].repeticao.parcelamento", nullValue())
        .body("itens[0].escoposDisponiveis", hasSize(2))
        .body("itens[0].escoposDisponiveis", hasItems("SOMENTE_ESTA", "ESTA_E_AS_PROXIMAS"));

    var contagemMaterializada =
        QuarkusTransaction.requiringNew()
            .call(() -> transacaoRepository.count("segmentoRecorrencia.id = ?1", segmentoId));
    assertEquals(0L, contagemMaterializada);
  }

  @Test
  void consultaSerieIndefinidaEmPeriodoDistanteSemExpandirAlemDoPeriodo() {
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                persistirSegmento(
                    "Aluguel",
                    TipoGrupoRecorrencia.RECORRENCIA,
                    "FREQ=MONTHLY;BYMONTHDAY=10",
                    LocalDate.of(2026, 1, 10),
                    PoliticaDataOcorrencia.PADRAO,
                    null,
                    null));

    requisicao(autenticar(), "2040-03", "2040-05")
        .statusCode(200)
        .body("itens", hasSize(3))
        .body("itens.dataFinanceira", contains("2040-03-10", "2040-04-10", "2040-05-10"))
        .body("itens.serie.dataOriginal", contains("2040-03-10", "2040-04-10", "2040-05-10"));
  }

  @Test
  void materializadaDeslocadaPreservaDataOriginalNaChaveEEnxergaSuaVersao() {
    var ids =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  var segmento =
                      persistirSegmento(
                          "Aluguel",
                          TipoGrupoRecorrencia.RECORRENCIA,
                          "FREQ=MONTHLY;BYMONTHDAY=10",
                          LocalDate.of(2026, 1, 10),
                          PoliticaDataOcorrencia.PADRAO,
                          null,
                          null);
                  var transacao = new Transacao();
                  transacao.setUsuario(segmento.getUsuario());
                  transacao.setConta(segmento.getConta());
                  transacao.setGrupoRecorrencia(segmento.getGrupo());
                  transacao.setSegmentoRecorrencia(segmento);
                  transacao.setTipo(TipoTransacao.DESPESA);
                  transacao.setSituacao(SituacaoTransacao.PLANEJADA);
                  transacao.setDescricao("Aluguel ajustado");
                  transacao.setValor(new BigDecimal("70.0000"));
                  transacao.setDataFinanceira(LocalDate.of(2026, 3, 12));
                  transacao.setIdentificadorRecorrencia(LocalDate.of(2026, 3, 10));
                  transacao.setExcecaoRecorrencia(true);
                  transacaoRepository.persistAndFlush(transacao);
                  return Map.of(
                      "segmento", segmento.getId(),
                      "transacao", transacao.getId(),
                      "versao", transacao.getVersao());
                });

    requisicao(autenticar(), "2026-03", "2026-03")
        .statusCode(200)
        .body("itens", hasSize(1))
        .body("itens[0].origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("itens[0].operacaoId", equalTo(ids.get("transacao").toString()))
        .body("itens[0].virtual", equalTo(false))
        .body("itens[0].protegida", equalTo(true))
        .body("itens[0].dataFinanceira", equalTo("2026-03-12"))
        .body("itens[0].serie.dataOriginal", equalTo("2026-03-10"))
        .body(
            "itens[0].chaveEdicao",
            equalTo(
                IdentidadeEdicaoTransacao.chaveRecorrencia(
                    (UUID) ids.get("segmento"), LocalDate.of(2026, 3, 10))))
        .body(
            "itens[0].versaoEdicao",
            equalTo(IdentidadeEdicaoTransacao.versaoRecorrencia(0L, (Long) ids.get("versao"))));
  }

  @Test
  void parcelamentoExpoeNumeracaoEConfiguracaoSemTermino() {
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                persistirSegmento(
                    "Notebook",
                    TipoGrupoRecorrencia.PARCELAMENTO,
                    "FREQ=MONTHLY;COUNT=3",
                    LocalDate.of(2026, 1, 31),
                    PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES,
                    2,
                    4));

    requisicao(autenticar(), "2026-02", "2026-02")
        .statusCode(200)
        .body("itens", hasSize(1))
        .body("itens[0].origem", equalTo("PARCELA"))
        .body("itens[0].dataFinanceira", equalTo("2026-02-28"))
        .body("itens[0].serie.dataOriginal", equalTo("2026-02-28"))
        .body("itens[0].serie.numeroParcela", equalTo(3))
        .body("itens[0].serie.totalParcelas", equalTo(4))
        .body("itens[0].repeticao.frequencia", equalTo("MENSAL"))
        .body("itens[0].repeticao.diasMes", empty())
        .body("itens[0].repeticao.politicaData", equalTo("AJUSTAR_ULTIMO_DIA_MES"))
        .body("itens[0].repeticao.termino", nullValue())
        .body("itens[0].repeticao.parcelamento.numeroPrimeiraParcela", equalTo(2))
        .body("itens[0].repeticao.parcelamento.totalParcelas", equalTo(4));
  }

  @Test
  void traduzInicioForaDoPrimeiroDiaDaSemanaSemRecalcularDias() {
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                persistirSegmento(
                    "Academia",
                    TipoGrupoRecorrencia.RECORRENCIA,
                    "FREQ=WEEKLY;BYDAY=MO,WE;WKST=MO",
                    LocalDate.of(2026, 1, 7),
                    PoliticaDataOcorrencia.PADRAO,
                    null,
                    null));

    requisicao(autenticar(), "2026-01", "2026-01")
        .statusCode(200)
        .body(
            "itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.dataFinanceira",
            equalTo("2026-01-07"))
        .body(
            "itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.serie.inicioSegmento",
            equalTo("2026-01-07"))
        .body(
            "itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.repeticao.diasSemana",
            contains("SEGUNDA", "QUARTA"))
        .body(
            "itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.repeticao.politicaData",
            equalTo("PADRAO"));
  }

  @Test
  void traduzMensalAntigaSemDiasDoMesComPoliticaPadrao() {
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                persistirSegmento(
                    "Assinatura",
                    TipoGrupoRecorrencia.RECORRENCIA,
                    "FREQ=MONTHLY",
                    LocalDate.of(2026, 1, 15),
                    PoliticaDataOcorrencia.PADRAO,
                    null,
                    null));

    requisicao(autenticar(), "2026-01", "2026-01")
        .statusCode(200)
        .body(
            "itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.dataFinanceira",
            equalTo("2026-01-15"))
        .body("itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.repeticao.diasMes", empty())
        .body("itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.repeticao.diasSemana", empty())
        .body(
            "itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.repeticao.politicaData",
            equalTo("PADRAO"))
        .body(
            "itens.find { it.origem == 'TRANSACAO_RECORRENTE' }.repeticao.termino.tipo",
            equalTo("INDEFINIDO"));
  }

  @Test
  void traduzTerminoPorDataEPorQuantidade() {
    var ids =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  var comData =
                      persistirSegmento(
                          "Curso",
                          TipoGrupoRecorrencia.RECORRENCIA,
                          "FREQ=DAILY;UNTIL=20260110",
                          LocalDate.of(2026, 1, 8),
                          PoliticaDataOcorrencia.PADRAO,
                          null,
                          null);
                  var comQuantidade =
                      persistirSegmento(
                          "Academia diaria",
                          TipoGrupoRecorrencia.RECORRENCIA,
                          "FREQ=DAILY;COUNT=5",
                          LocalDate.of(2026, 1, 20),
                          PoliticaDataOcorrencia.PADRAO,
                          null,
                          null);
                  return Map.of(
                      "data", comData.getId().toString(),
                      "quantidade", comQuantidade.getId().toString());
                });

    List<Map<String, Object>> itens =
        requisicao(autenticar(), "2026-01", "2026-01")
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("itens");
    var comData = itemDaSerie(itens, ids.get("data"));
    var comQuantidade = itemDaSerie(itens, ids.get("quantidade"));
    var terminoData = termino(comData);
    var terminoQuantidade = termino(comQuantidade);
    assertEquals("EM_DATA", terminoData.get("tipo"));
    assertEquals("2026-01-10", terminoData.get("data"));
    assertEquals("APOS_OCORRENCIAS", terminoQuantidade.get("tipo"));
    assertEquals(5, terminoQuantidade.get("quantidade"));
  }

  @Test
  void itemSimplesNaoExpoeMetadadosDeSerieEOutroUsuarioNaoVeOsItens() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              var conta = contaRepository.findById(contaId);
              var transacao = new Transacao();
              transacao.setUsuario(conta.getUsuario());
              transacao.setConta(conta);
              transacao.setTipo(TipoTransacao.RECEITA);
              transacao.setSituacao(SituacaoTransacao.EFETIVADA);
              transacao.setDescricao("Salario");
              transacao.setValor(new BigDecimal("20.0000"));
              transacao.setDataFinanceira(LocalDate.of(2026, 1, 5));
              transacao.setEfetivadoEm(Instant.now());
              transacaoRepository.persistAndFlush(transacao);
            });

    requisicao(autenticar(), "2026-01", "2026-01")
        .statusCode(200)
        .body("itens.find { it.origem == 'TRANSACAO_SIMPLES' }.serie", nullValue())
        .body("itens.find { it.origem == 'TRANSACAO_SIMPLES' }.repeticao", nullValue())
        .body("itens.find { it.origem == 'TRANSACAO_SIMPLES' }.virtual", equalTo(false))
        .body("itens.find { it.origem == 'TRANSACAO_SIMPLES' }.protegida", equalTo(false))
        .body("itens.find { it.origem == 'TRANSACAO_SIMPLES' }.escoposDisponiveis", empty())
        .body("itens.find { it.origem == 'TRANSACAO_SIMPLES' }.chaveEdicao", notNullValue());

    requisicao(autenticarOutroUsuario(), "2026-01", "2026-01")
        .statusCode(200)
        .body("itens", empty());
  }

  private ValidatableResponse requisicao(String token, String inicio, String fim) {
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

  private SegmentoRecorrencia persistirSegmento(
      String descricao,
      TipoGrupoRecorrencia tipo,
      String rrule,
      LocalDate inicio,
      PoliticaDataOcorrencia politica,
      Integer numeroPrimeiraParcela,
      Integer quantidadeTotalOriginal) {
    var usuario = usuarioRepository.findById(usuarioId);
    var conta = contaRepository.findById(contaId);
    var grupo = new GrupoRecorrencia();
    grupo.setUsuario(usuario);
    grupo.setDescricao(descricao);
    grupo.setTipo(tipo);
    grupo.setStatus(StatusRecorrencia.ATIVO);
    grupoRepository.persist(grupo);

    var segmento = new SegmentoRecorrencia();
    segmento.setUsuario(usuario);
    segmento.setGrupo(grupo);
    segmento.setConta(conta);
    segmento.setTipo(TipoTransacao.DESPESA);
    segmento.setDescricao(descricao);
    segmento.setValor(new BigDecimal("50.0000"));
    segmento.setInicio(inicio);
    segmento.setRrule(rrule);
    segmento.setNumeroPrimeiraParcela(numeroPrimeiraParcela);
    segmento.setQuantidadeTotalOriginal(quantidadeTotalOriginal);
    segmento.setPoliticaData(politica);
    segmento.setStatus(StatusRecorrencia.ATIVO);
    segmentoRepository.persist(segmento);
    return segmento;
  }

  private Usuario novoUsuario(String email, String nome, Argon2 argon2, String senha) {
    var usuario = new Usuario();
    usuario.setNome(nome);
    usuario.setEmail(email);
    usuario.setSenhaHash(argon2.hash(2, 19_456, 1, senha.toCharArray()));
    usuario.setTimezone(FUSO.getId());
    usuario.setAtivo(true);
    usuarioRepository.persist(usuario);
    return usuario;
  }

  private static Map<?, ?> itemDaSerie(List<Map<String, Object>> itens, String segmentoId) {
    return itens.stream()
        .filter(
            item ->
                item.get("serie") != null
                    && segmentoId.equals(((Map<?, ?>) item.get("serie")).get("segmentoId")))
        .findFirst()
        .orElseThrow();
  }

  private static Map<?, ?> termino(Map<?, ?> item) {
    return (Map<?, ?>) ((Map<?, ?>) item.get("repeticao")).get("termino");
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
}
