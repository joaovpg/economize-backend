package com.joaovpg.economize.transacao;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.BIG_DECIMAL;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
class CriarRepeticaoTransacaoResourceTest {
  private static final ZoneId FUSO = ZoneId.of("Pacific/Pago_Pago");

  private static final String MENSAL_DIA_10_INDEFINIDA =
      repeticao("MENSAL", "[]", "[10]", "PADRAO", indefinido(), "null");

  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;
  @Inject CategoriaRepository categoriaRepository;
  @Inject TransacaoRepository transacaoRepository;

  private String email;
  private String emailOutroUsuario;
  private UUID usuarioId;
  private UUID outroUsuarioId;
  private UUID contaId;

  @BeforeEach
  void prepararDados() {
    email = "repeticao-" + UUID.randomUUID() + "@example.com";
    emailOutroUsuario = "repeticao-outra-" + UUID.randomUUID() + "@example.com";
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
              outroUsuarioId = outro.getId();
            });
  }

  @Test
  void consultaOSaldoDeAberturaComAsOcorrenciasAnterioresAoPeriodo() {
    var token = autenticar();
    criar(token, corpo("EFETIVADA", "2026-10-10", MENSAL_DIA_10_INDEFINIDA)).statusCode(201);

    consultar(token, "2026-11", "2026-11")
        .statusCode(200)
        .body("saldoAbertura", equalTo(new BigDecimal("-1500.0000")))
        .body("itens.dataFinanceira", contains("2026-11-10"))
        .body("itens.valor", contains(new BigDecimal("-1500.0000")));
  }

  @Test
  void ocultaRecursosAlheiosOuInativosESemCriarSerie() {
    var token = autenticar();
    var alheios = criarRecursosAlheios();
    var tokenOutro = autenticarOutroUsuario();

    criar(
            token,
            corpoComRecursos(
                alheios.contaInativa(), null, "PLANEJADA", "2026-11-10", MENSAL_DIA_10_INDEFINIDA))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    criar(
            token,
            corpoComRecursos(
                contaId,
                alheios.categoriaInativa(),
                "PLANEJADA",
                "2026-11-10",
                MENSAL_DIA_10_INDEFINIDA))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    criar(
            token,
            corpoComRecursos(
                contaId,
                alheios.categoriaOutroUsuario(),
                "PLANEJADA",
                "2026-11-10",
                MENSAL_DIA_10_INDEFINIDA))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    criar(
            token,
            corpoComRecursos(
                alheios.contaOutroUsuario(),
                null,
                "PLANEJADA",
                "2026-11-10",
                MENSAL_DIA_10_INDEFINIDA))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));
    criar(tokenOutro, corpo("PLANEJADA", "2026-11-10", MENSAL_DIA_10_INDEFINIDA))
        .statusCode(404)
        .body("type", equalTo("urn:economize:problem:RECURSO_NAO_ENCONTRADO"));

    consultar(token, "2026-11", "2026-12").statusCode(200).body("itens", empty());
    consultar(tokenOutro, "2026-11", "2026-12").statusCode(200).body("itens", empty());
  }

  @Test
  void rejeitaEfetivadaComDataFuturaOuAnteriorAoSaldoSemCriarSerie() {
    var token = autenticar();
    criar(token, corpo("EFETIVADA", "2099-01-10", MENSAL_DIA_10_INDEFINIDA))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_FUTURA"));
    criar(token, corpo("PLANEJADA", "2025-12-10", MENSAL_DIA_10_INDEFINIDA))
        .statusCode(422)
        .body("type", equalTo("urn:economize:problem:DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL"));

    consultar(token, "2025-12", "2026-11").statusCode(200).body("itens", empty());
  }

  private RecursosAlheios criarRecursosAlheios() {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              var usuario = usuarioRepository.findById(usuarioId);
              var outro = usuarioRepository.findById(outroUsuarioId);

              var contaInativa = novaConta(usuario, "Conta inativa");
              contaInativa.setAtivo(false);
              contaRepository.persist(contaInativa);

              var categoriaInativa = novaCategoria(usuario, "Arquivada", false);
              var categoriaOutroUsuario = novaCategoria(outro, "Categoria alheia", true);

              var contaOutroUsuario = novaConta(outro, "Conta alheia");
              contaRepository.persist(contaOutroUsuario);
              contaRepository.flush();
              return new RecursosAlheios(
                  contaInativa.getId(),
                  categoriaInativa.getId(),
                  categoriaOutroUsuario.getId(),
                  contaOutroUsuario.getId());
            });
  }

  private ContaFinanceira novaConta(Usuario usuario, String nome) {
    var conta = new ContaFinanceira();
    conta.setUsuario(usuario);
    conta.setNome(nome);
    conta.setMoeda("BRL");
    conta.setSaldoInicial(BigDecimal.ZERO);
    conta.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
    return conta;
  }

  private Categoria novaCategoria(Usuario usuario, String nome, boolean ativa) {
    var categoria = new Categoria();
    categoria.setUsuario(usuario);
    categoria.setNome(nome);
    categoria.setAtivo(ativa);
    categoriaRepository.persist(categoria);
    return categoria;
  }

  private record RecursosAlheios(
      UUID contaInativa,
      UUID categoriaInativa,
      UUID categoriaOutroUsuario,
      UUID contaOutroUsuario) {}

  @Test
  void criaAluguelMensalIndefinidoRespondeAPrimeiraOcorrenciaVirtualComoItemDeLeitura() {
    criar(autenticar(), corpo("PLANEJADA", "2026-11-10", MENSAL_DIA_10_INDEFINIDA))
        .statusCode(201)
        .header("Location", notNullValue())
        .body("origem", equalTo("TRANSACAO_RECORRENTE"))
        .body("operacaoId", nullValue())
        .body("virtual", equalTo(true))
        .body("protegida", equalTo(false))
        .body("situacao", equalTo("PLANEJADA"))
        .body("valor", equalTo(new BigDecimal("-1500.0000")))
        .body("dataFinanceira", equalTo("2026-11-10"))
        .body("chaveEdicao", notNullValue())
        .body("versaoEdicao", notNullValue())
        .body("serie.grupoId", notNullValue())
        .body("serie.segmentoId", notNullValue())
        .body("serie.inicioSegmento", equalTo("2026-11-10"))
        .body("serie.dataOriginal", equalTo("2026-11-10"))
        .body("serie.numeroParcela", nullValue())
        .body("serie.totalParcelas", nullValue())
        .body("repeticao.frequencia", equalTo("MENSAL"))
        .body("repeticao.intervalo", equalTo(1))
        .body("repeticao.diasSemana", hasSize(0))
        .body("repeticao.diasMes", contains(10))
        .body("repeticao.politicaData", equalTo("PADRAO"))
        .body("repeticao.termino.tipo", equalTo("INDEFINIDO"))
        .body("repeticao.parcelamento", nullValue())
        .body("escoposDisponiveis", hasSize(2));
  }

  @Test
  void projetaOcorrenciasFuturasSemMaterializarNaLeitura() {
    var token = autenticar();
    String segmentoId =
        criar(token, corpo("PLANEJADA", "2026-11-10", MENSAL_DIA_10_INDEFINIDA))
            .statusCode(201)
            .extract()
            .path("serie.segmentoId");

    consultar(token, "2026-11", "2026-12")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-11-10", "2026-12-10"))
        .body("itens.virtual", contains(true, true))
        .body("itens.serie.segmentoId", contains(segmentoId, segmentoId));

    assertEquals(0L, materializadas(segmentoId));
  }

  @Test
  void efetivadaMaterializaAPrimeiraOcorrenciaUmaUnicaVez() {
    var token = autenticar();
    String segmentoId =
        criar(token, corpo("EFETIVADA", "2026-10-10", MENSAL_DIA_10_INDEFINIDA))
            .statusCode(201)
            .body("origem", equalTo("TRANSACAO_RECORRENTE"))
            .body("virtual", equalTo(false))
            .body("protegida", equalTo(true))
            .body("situacao", equalTo("EFETIVADA"))
            .body("efetivadoEm", notNullValue())
            .body("operacaoId", notNullValue())
            .body("dataFinanceira", equalTo("2026-10-10"))
            .body("serie.dataOriginal", equalTo("2026-10-10"))
            .extract()
            .path("serie.segmentoId");

    consultar(token, "2026-10", "2026-11")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-10-10", "2026-11-10"))
        .body("itens.virtual", contains(false, true))
        .body("itens.situacao", contains("EFETIVADA", "PLANEJADA"));

    assertEquals(1L, materializadas(segmentoId));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("casosValidos")
  void aceitaCombinacoesValidasEDevolveAConfiguracaoEfetiva(
      String nome, String dataFinanceira, String repeticao, String frequencia, String politica) {
    criar(autenticar(), corpo("PLANEJADA", dataFinanceira, repeticao))
        .statusCode(201)
        .body("virtual", equalTo(true))
        .body("dataFinanceira", equalTo(dataFinanceira))
        .body("repeticao.frequencia", equalTo(frequencia))
        .body("repeticao.politicaData", equalTo(politica));
  }

  static Stream<Arguments> casosValidos() {
    var sem = "null";
    var parcelado = "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}";
    return Stream.of(
        Arguments.of(
            "diaria indefinida",
            "2026-11-10",
            repeticao("DIARIA", "[]", "[]", "PADRAO", indefinido(), sem),
            "DIARIA",
            "PADRAO"),
        Arguments.of(
            "semanal com dois dias da semana",
            "2026-11-10",
            repeticao("SEMANAL", "[\"QUINTA\",\"TERCA\"]", "[]", "PADRAO", indefinido(), sem),
            "SEMANAL",
            "PADRAO"),
        Arguments.of(
            "mensal com dia do mes",
            "2026-11-10",
            repeticao("MENSAL", "[]", "[10]", "PADRAO", indefinido(), sem),
            "MENSAL",
            "PADRAO"),
        Arguments.of(
            "mensal ajustada ao ultimo dia sem dias informados",
            "2026-11-30",
            repeticao("MENSAL", "[]", "[]", "AJUSTAR_ULTIMO_DIA_MES", indefinido(), sem),
            "MENSAL",
            "AJUSTAR_ULTIMO_DIA_MES"),
        Arguments.of(
            "anual padrao",
            "2026-11-10",
            repeticao("ANUAL", "[]", "[]", "PADRAO", indefinido(), sem),
            "ANUAL",
            "PADRAO"),
        Arguments.of(
            "anual ajustada ao ultimo dia",
            "2026-11-30",
            repeticao("ANUAL", "[]", "[]", "AJUSTAR_ULTIMO_DIA_MES", indefinido(), sem),
            "ANUAL",
            "AJUSTAR_ULTIMO_DIA_MES"),
        Arguments.of(
            "semanal com termino em data",
            "2026-11-10",
            repeticao(
                "SEMANAL",
                "[\"TERCA\"]",
                "[]",
                "PADRAO",
                "{\"tipo\":\"EM_DATA\",\"data\":\"2026-12-31\"}",
                sem),
            "SEMANAL",
            "PADRAO"),
        Arguments.of(
            "mensal com termino apos ocorrencias",
            "2026-11-10",
            repeticao(
                "MENSAL",
                "[]",
                "[10]",
                "PADRAO",
                "{\"tipo\":\"APOS_OCORRENCIAS\",\"quantidade\":3}",
                sem),
            "MENSAL",
            "PADRAO"),
        Arguments.of(
            "diaria parcelada",
            "2026-11-10",
            repeticao("DIARIA", "[]", "[]", "PADRAO", sem, parcelado),
            "DIARIA",
            "PADRAO"),
        Arguments.of(
            "semanal parcelada no dia da data financeira",
            "2026-11-10",
            repeticao("SEMANAL", "[\"TERCA\"]", "[]", "PADRAO", sem, parcelado),
            "SEMANAL",
            "PADRAO"),
        Arguments.of(
            "mensal parcelada ajustada",
            "2026-11-30",
            repeticao("MENSAL", "[]", "[]", "AJUSTAR_ULTIMO_DIA_MES", sem, parcelado),
            "MENSAL",
            "AJUSTAR_ULTIMO_DIA_MES"),
        Arguments.of(
            "mensal parcelada com politica padrao",
            "2026-11-10",
            repeticao("MENSAL", "[]", "[]", "PADRAO", sem, parcelado),
            "MENSAL",
            "PADRAO"),
        Arguments.of(
            "anual parcelada ajustada",
            "2026-11-30",
            repeticao("ANUAL", "[]", "[]", "AJUSTAR_ULTIMO_DIA_MES", sem, parcelado),
            "ANUAL",
            "AJUSTAR_ULTIMO_DIA_MES"));
  }

  @Test
  void criaAluguelMensalEmVariasDatasDoDiaCinco() {
    var token = autenticar();
    for (var data :
        new String[] {
          "2026-01-05",
          "2026-02-05",
          "2026-03-05",
          "2026-11-05",
          "2026-12-05",
          "2027-01-05",
          "2028-02-05",
          "2099-01-05"
        }) {
      var resposta =
          criar(
                  token,
                  corpo(
                      "PLANEJADA",
                      data,
                      repeticao("MENSAL", "[]", "[5]", "PADRAO", indefinido(), "null")))
              .extract()
              .response();
      assertEquals(201, resposta.statusCode(), "data " + data + ": " + resposta.asString());
    }
  }

  @Test
  void tokenInvalidoRespondeNaoAutorizadoEmVezDeErroInterno() {
    given()
        .cookie("economize_token", "token-que-nao-foi-assinado-por-este-servidor")
        .contentType("application/json")
        .body(corpo("PLANEJADA", "2026-11-05", MENSAL_DIA_10_INDEFINIDA))
        .when()
        .post("/api/transacoes")
        .then()
        .statusCode(401);
  }

  @Test
  void criaAluguelMensalComCategoriaEValorInteiro() {
    var categoriaId =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  var categoria = new Categoria();
                  categoria.setUsuario(usuarioRepository.findById(usuarioId));
                  categoria.setNome("Moradia");
                  categoria.setAtivo(true);
                  categoriaRepository.persist(categoria);
                  return categoria.getId();
                });
    var token = autenticar();
    var corpo =
        """
        {"tipo":"DESPESA","contaId":"%s","categoriaId":"%s","situacao":"PLANEJADA",
         "descricao":"Aluguel","observacoes":null,"valor":1500,"dataFinanceira":"2026-11-05",
         "repeticao":{"frequencia":"MENSAL","intervalo":1,"diasSemana":[],"diasMes":[5],
           "politicaData":"PADRAO","termino":{"tipo":"INDEFINIDO"},"parcelamento":null}}
        """
            .formatted(contaId, categoriaId);

    criar(token, corpo).statusCode(201).body("categoriaId", equalTo(categoriaId.toString()));
  }

  @Test
  void projetaTerminoEmDataComDataFinalInclusiva() {
    var token = autenticar();
    criar(
            token,
            corpo(
                "PLANEJADA",
                "2026-11-10",
                repeticao(
                    "MENSAL",
                    "[]",
                    "[10]",
                    "PADRAO",
                    "{\"tipo\":\"EM_DATA\",\"data\":\"2027-01-10\"}",
                    "null")))
        .statusCode(201)
        .body("repeticao.termino.tipo", equalTo("EM_DATA"))
        .body("repeticao.termino.data", equalTo("2027-01-10"));

    consultar(token, "2026-11", "2027-02")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-11-10", "2026-12-10", "2027-01-10"));
  }

  @Test
  void projetaTerminoAposOcorrenciasContandoAPrimeira() {
    var token = autenticar();
    criar(
            token,
            corpo(
                "PLANEJADA",
                "2026-11-10",
                repeticao(
                    "MENSAL",
                    "[]",
                    "[10]",
                    "PADRAO",
                    "{\"tipo\":\"APOS_OCORRENCIAS\",\"quantidade\":2}",
                    "null")))
        .statusCode(201)
        .body("repeticao.termino.tipo", equalTo("APOS_OCORRENCIAS"))
        .body("repeticao.termino.quantidade", equalTo(2));

    consultar(token, "2026-11", "2027-03")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-11-10", "2026-12-10"));
  }

  @Test
  void numeraParcelasENaoProjetaAlemDaQuantidadeRestante() {
    var token = autenticar();
    criar(
            token,
            corpo(
                "PLANEJADA",
                "2026-11-30",
                repeticao(
                    "MENSAL",
                    "[]",
                    "[]",
                    "AJUSTAR_ULTIMO_DIA_MES",
                    "null",
                    "{\"numeroPrimeiraParcela\":3,\"totalParcelas\":5}")))
        .statusCode(201)
        .body("serie.numeroParcela", equalTo(3))
        .body("serie.totalParcelas", equalTo(5))
        .body("repeticao.termino", nullValue())
        .body("repeticao.parcelamento.numeroPrimeiraParcela", equalTo(3))
        .body("repeticao.parcelamento.totalParcelas", equalTo(5));

    consultar(token, "2026-11", "2027-03")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-11-30", "2026-12-30", "2027-01-30"))
        .body("itens.serie.numeroParcela", contains(3, 4, 5))
        .body("itens.serie.totalParcelas", contains(5, 5, 5));
  }

  @Test
  void padraoPulaMesesSemODiaTrintaEEUmUltimoDiaAjustaCadaMes() {
    var token = autenticar();
    criar(
            token,
            corpo(
                "PLANEJADA",
                "2026-03-31",
                repeticao("MENSAL", "[]", "[31]", "PADRAO", indefinido(), "null")))
        .statusCode(201);

    consultar(token, "2026-03", "2026-07")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-03-31", "2026-05-31", "2026-07-31"));
  }

  @Test
  void ajusteDoUltimoDiaMantemAsDatasDeFimDeMes() {
    var token = autenticar();
    criar(
            token,
            corpo(
                "PLANEJADA",
                "2026-03-31",
                repeticao("MENSAL", "[]", "[]", "AJUSTAR_ULTIMO_DIA_MES", indefinido(), "null")))
        .statusCode(201);

    consultar(token, "2026-03", "2026-07")
        .statusCode(200)
        .body(
            "itens.dataFinanceira",
            contains("2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30", "2026-07-31"));
  }

  @Test
  void ajusteAnualDeFevereiroBissextoUsaUltimoDiaDoMes() {
    var token = autenticar();
    criar(
            token,
            corpo(
                "PLANEJADA",
                "2028-02-29",
                repeticao("ANUAL", "[]", "[]", "AJUSTAR_ULTIMO_DIA_MES", indefinido(), "null")))
        .statusCode(201);

    consultar(token, "2028-02", "2028-02")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2028-02-29"));
    consultar(token, "2029-02", "2029-02")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2029-02-28"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("casosInvalidos")
  void rejeitaCombinacoesInvalidasComOCodigoDaEspecificacao(
      String nome, String dataFinanceira, String repeticao, int status, String codigo) {
    criar(autenticar(), corpo("PLANEJADA", dataFinanceira, repeticao))
        .statusCode(status)
        .body("type", equalTo("urn:economize:problem:" + codigo));
  }

  static Stream<Arguments> casosInvalidos() {
    var terca = "2026-11-10";
    var mensal = "[10]";
    return Stream.of(
        // Frequência e dias
        Arguments.of(
            "diaria com dia da semana",
            terca,
            repeticao("DIARIA", "[\"SEGUNDA\"]", "[]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "diaria com dia do mes",
            terca,
            repeticao("DIARIA", "[]", mensal, "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "semanal sem dias da semana",
            terca,
            repeticao("SEMANAL", "[]", "[]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "semanal que nao inclui o dia da data financeira",
            terca,
            repeticao("SEMANAL", "[\"SEGUNDA\"]", "[]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "semanal com dia anterior ao inicio",
            terca,
            repeticao("SEMANAL", "[\"SEGUNDA\",\"TERCA\"]", "[]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "semanal com dia do mes",
            terca,
            repeticao("SEMANAL", "[\"TERCA\"]", mensal, "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "mensal padrao sem dias do mes",
            terca,
            repeticao("MENSAL", "[]", "[]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "mensal padrao sem o dia da data financeira",
            terca,
            repeticao("MENSAL", "[]", "[5]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "mensal padrao com dia anterior ao inicio",
            terca,
            repeticao("MENSAL", "[]", "[5,10]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "mensal com dia da semana",
            terca,
            repeticao("MENSAL", "[\"TERCA\"]", mensal, "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "anual com dia do mes",
            terca,
            repeticao("ANUAL", "[]", mensal, "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "anual com dia da semana",
            terca,
            repeticao("ANUAL", "[\"TERCA\"]", "[]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "dia do mes repetido",
            terca,
            repeticao("MENSAL", "[]", "[10,10]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "dia do mes fora de 1 a 31",
            terca,
            repeticao("MENSAL", "[]", "[32]", "PADRAO", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "intervalo zero",
            terca,
            """
            {"frequencia":"MENSAL","intervalo":0,"diasSemana":[],"diasMes":[10],
             "politicaData":"PADRAO","termino":{"tipo":"INDEFINIDO"},"parcelamento":null}
            """,
            422,
            "REPETICAO_INVALIDA"),
        // Política de datas
        Arguments.of(
            "ajuste de ultimo dia em repeticao diaria",
            terca,
            repeticao("DIARIA", "[]", "[]", "AJUSTAR_ULTIMO_DIA_MES", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "ajuste de ultimo dia em repeticao semanal",
            terca,
            repeticao(
                "SEMANAL", "[\"TERCA\"]", "[]", "AJUSTAR_ULTIMO_DIA_MES", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "ajuste de ultimo dia com dias do mes",
            terca,
            repeticao("MENSAL", "[]", mensal, "AJUSTAR_ULTIMO_DIA_MES", indefinido(), "null"),
            422,
            "REPETICAO_INVALIDA"),
        // Término
        Arguments.of(
            "indefinido com data",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                mensal,
                "PADRAO",
                "{\"tipo\":\"INDEFINIDO\",\"data\":\"2027-01-10\"}",
                "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino em data anterior ao inicio",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                mensal,
                "PADRAO",
                "{\"tipo\":\"EM_DATA\",\"data\":\"2026-11-01\"}",
                "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino em data com quantidade",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                mensal,
                "PADRAO",
                "{\"tipo\":\"EM_DATA\",\"data\":\"2027-01-10\",\"quantidade\":3}",
                "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino apos zero ocorrencias",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                mensal,
                "PADRAO",
                "{\"tipo\":\"APOS_OCORRENCIAS\",\"quantidade\":0}",
                "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino apos ocorrencias com data",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                mensal,
                "PADRAO",
                "{\"tipo\":\"APOS_OCORRENCIAS\",\"quantidade\":2,\"data\":\"2027-01-10\"}",
                "null"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "repeticao sem termino nem parcelamento",
            terca,
            repeticao("MENSAL", "[]", mensal, "PADRAO", "null", "null"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "termino em data sem data",
            terca,
            repeticao("MENSAL", "[]", mensal, "PADRAO", "{\"tipo\":\"EM_DATA\"}", "null"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "termino apos ocorrencias sem quantidade",
            terca,
            repeticao("MENSAL", "[]", mensal, "PADRAO", "{\"tipo\":\"APOS_OCORRENCIAS\"}", "null"),
            400,
            "DADOS_INVALIDOS"),
        // Parcelamento
        Arguments.of(
            "parcelamento com termino",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                "[]",
                "AJUSTAR_ULTIMO_DIA_MES",
                indefinido(),
                "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}"),
            422,
            "PARCELAMENTO_INVALIDO"),
        Arguments.of(
            "parcelamento com primeira parcela zero",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                "[]",
                "AJUSTAR_ULTIMO_DIA_MES",
                "null",
                "{\"numeroPrimeiraParcela\":0,\"totalParcelas\":3}"),
            422,
            "PARCELAMENTO_INVALIDO"),
        Arguments.of(
            "parcelamento com primeira parcela maior que o total",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                "[]",
                "AJUSTAR_ULTIMO_DIA_MES",
                "null",
                "{\"numeroPrimeiraParcela\":4,\"totalParcelas\":3}"),
            422,
            "PARCELAMENTO_INVALIDO"),
        Arguments.of(
            "parcelamento semanal sem dia da semana",
            terca,
            repeticao(
                "SEMANAL",
                "[]",
                "[]",
                "PADRAO",
                "null",
                "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "parcelamento semanal com dias diferentes da data financeira",
            terca,
            repeticao(
                "SEMANAL",
                "[\"TERCA\",\"QUINTA\"]",
                "[]",
                "PADRAO",
                "null",
                "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "parcelamento mensal com dia do mes",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                mensal,
                "PADRAO",
                "null",
                "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "parcelamento diario com ajuste de ultimo dia",
            terca,
            repeticao(
                "DIARIA",
                "[]",
                "[]",
                "AJUSTAR_ULTIMO_DIA_MES",
                "null",
                "{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3}"),
            422,
            "REPETICAO_INVALIDA"),
        // Contrato HTTP: campos ausentes, desconhecidos ou fora dos nomes públicos
        Arguments.of("repeticao vazia", terca, "{}", 400, "DADOS_INVALIDOS"),
        Arguments.of(
            "politica de datas ausente",
            terca,
            """
            {"frequencia":"MENSAL","intervalo":1,"diasSemana":[],"diasMes":[10],
             "termino":{"tipo":"INDEFINIDO"},"parcelamento":null}
            """,
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "campo desconhecido na repeticao",
            terca,
            """
            {"frequencia":"MENSAL","intervalo":1,"diasSemana":[],"diasMes":[10],
             "politicaData":"PADRAO","termino":{"tipo":"INDEFINIDO"},"parcelamento":null,
             "extra":true}
            """,
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "frequencia em ingles",
            terca,
            repeticao("DAILY", "[]", "[]", "PADRAO", indefinido(), "null"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "dia da semana em ingles",
            terca,
            repeticao("SEMANAL", "[\"MONDAY\"]", "[]", "PADRAO", indefinido(), "null"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "tipo de termino desconhecido",
            terca,
            repeticao("MENSAL", "[]", mensal, "PADRAO", "{\"tipo\":\"XYZ\"}", "null"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "parcelamento sem total",
            terca,
            repeticao(
                "MENSAL",
                "[]",
                "[]",
                "AJUSTAR_ULTIMO_DIA_MES",
                "null",
                "{\"numeroPrimeiraParcela\":1}"),
            400,
            "DADOS_INVALIDOS"));
  }

  private ValidatableResponse criar(String token, String corpo) {
    return given()
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
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
        .config(config().jsonConfig(jsonConfig().numberReturnType(BIG_DECIMAL)))
        .auth()
        .oauth2(token)
        .queryParam("inicio", inicio)
        .queryParam("fim", fim)
        .when()
        .get("/api/transacoes")
        .then();
  }

  private long materializadas(String segmentoId) {
    var id = UUID.fromString(segmentoId);
    return QuarkusTransaction.requiringNew()
        .call(() -> transacaoRepository.count("segmentoRecorrencia.id = ?1", id));
  }

  private String corpo(String situacao, String dataFinanceira, String repeticao) {
    return corpoComRecursos(contaId, null, situacao, dataFinanceira, repeticao);
  }

  private String corpoComRecursos(
      UUID conta, UUID categoria, String situacao, String dataFinanceira, String repeticao) {
    var categoriaJson = categoria == null ? "null" : "\"" + categoria + "\"";
    return """
    {"tipo":"DESPESA","contaId":"%s","categoriaId":%s,"descricao":"Aluguel",
     "observacoes":null,"valor":1500.00,"dataFinanceira":"%s","situacao":"%s",
     "repeticao":%s}
    """
        .formatted(conta, categoriaJson, dataFinanceira, situacao, repeticao);
  }

  private String autenticarOutroUsuario() {
    return login(emailOutroUsuario, "outra-senha");
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

  private static String indefinido() {
    return "{\"tipo\":\"INDEFINIDO\"}";
  }

  private String autenticar() {
    return login(email, "senha-segura");
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
