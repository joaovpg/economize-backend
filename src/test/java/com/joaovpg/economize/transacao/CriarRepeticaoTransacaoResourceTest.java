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
      repeticao("MENSAL", "DIA_DO_MES", indefinido());

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
        .body("repeticao.ancoragem", equalTo("DIA_DO_MES"))
        .body("repeticao.numeroDiaUtil", nullValue())
        .body("repeticao.termino.tipo", equalTo("INDEFINIDO"))
        .body("repeticao.termino.numeroPrimeiraParcela", nullValue())
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
      String nome,
      String dataFinanceira,
      String repeticao,
      String frequencia,
      String ancoragem,
      String tipoTermino) {
    criar(autenticar(), corpo("PLANEJADA", dataFinanceira, repeticao))
        .statusCode(201)
        .body("virtual", equalTo(true))
        .body("dataFinanceira", equalTo(dataFinanceira))
        .body("repeticao.frequencia", equalTo(frequencia))
        .body("repeticao.ancoragem", equalTo(ancoragem))
        .body("repeticao.termino.tipo", equalTo(tipoTermino));
  }

  static Stream<Arguments> casosValidos() {
    return Stream.of(
        Arguments.of(
            "diaria indefinida",
            "2026-11-10",
            repeticao("DIARIA", "DIA_DO_MES", indefinido()),
            "DIARIA",
            "DIA_DO_MES",
            "INDEFINIDO"),
        Arguments.of(
            "semanal a cada duas semanas",
            "2026-11-10",
            repeticao("SEMANAL", 2, "DIA_DO_MES", indefinido()),
            "SEMANAL",
            "DIA_DO_MES",
            "INDEFINIDO"),
        Arguments.of(
            "mensal com dia do mes",
            "2026-11-10",
            repeticao("MENSAL", "DIA_DO_MES", indefinido()),
            "MENSAL",
            "DIA_DO_MES",
            "INDEFINIDO"),
        Arguments.of(
            "mensal no ultimo dia do mes em data de fim de mes",
            "2026-11-30",
            repeticao("MENSAL", "DIA_DO_MES", indefinido()),
            "MENSAL",
            "DIA_DO_MES",
            "INDEFINIDO"),
        Arguments.of(
            "mensal no quinto dia util",
            "2026-11-05",
            repeticao("MENSAL", "DIA_UTIL", indefinido()),
            "MENSAL",
            "DIA_UTIL",
            "INDEFINIDO"),
        Arguments.of(
            "anual no dia do mes",
            "2026-11-10",
            repeticao("ANUAL", "DIA_DO_MES", indefinido()),
            "ANUAL",
            "DIA_DO_MES",
            "INDEFINIDO"),
        Arguments.of(
            "semanal com termino em data",
            "2026-11-10",
            repeticao("SEMANAL", "DIA_DO_MES", emData("2026-12-31")),
            "SEMANAL",
            "DIA_DO_MES",
            "EM_DATA"),
        Arguments.of(
            "mensal com termino apos ocorrencias",
            "2026-11-10",
            repeticao("MENSAL", "DIA_DO_MES", apos(3)),
            "MENSAL",
            "DIA_DO_MES",
            "APOS_OCORRENCIAS"),
        Arguments.of(
            "diaria parcelada",
            "2026-11-10",
            repeticao("DIARIA", "DIA_DO_MES", parcelas(1, 3)),
            "DIARIA",
            "DIA_DO_MES",
            "PARCELAS"),
        Arguments.of(
            "semanal parcelada",
            "2026-11-10",
            repeticao("SEMANAL", "DIA_DO_MES", parcelas(1, 3)),
            "SEMANAL",
            "DIA_DO_MES",
            "PARCELAS"),
        Arguments.of(
            "mensal parcelada no ultimo dia do mes",
            "2026-11-30",
            repeticao("MENSAL", "DIA_DO_MES", parcelas(1, 3)),
            "MENSAL",
            "DIA_DO_MES",
            "PARCELAS"),
        Arguments.of(
            "mensal parcelada no dia util",
            "2026-11-05",
            repeticao("MENSAL", "DIA_UTIL", parcelas(2, 4)),
            "MENSAL",
            "DIA_UTIL",
            "PARCELAS"),
        Arguments.of(
            "anual parcelada",
            "2026-11-30",
            repeticao("ANUAL", "DIA_DO_MES", parcelas(1, 3)),
            "ANUAL",
            "DIA_DO_MES",
            "PARCELAS"));
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
          criar(token, corpo("PLANEJADA", data, repeticao("MENSAL", "DIA_DO_MES", indefinido())))
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
         "repeticao":{"frequencia":"MENSAL","intervalo":1,"ancoragem":"DIA_DO_MES",
           "termino":{"tipo":"INDEFINIDO"}}}
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
                "PLANEJADA", "2026-11-10", repeticao("MENSAL", "DIA_DO_MES", emData("2027-01-10"))))
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
    criar(token, corpo("PLANEJADA", "2026-11-10", repeticao("MENSAL", "DIA_DO_MES", apos(2))))
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
            corpo("PLANEJADA", "2026-11-30", repeticao("MENSAL", "DIA_DO_MES", parcelas(3, 5))))
        .statusCode(201)
        .body("serie.numeroParcela", equalTo(3))
        .body("serie.totalParcelas", equalTo(5))
        .body("repeticao.termino.tipo", equalTo("PARCELAS"))
        .body("repeticao.termino.numeroPrimeiraParcela", equalTo(3))
        .body("repeticao.termino.totalParcelas", equalTo(5))
        .body("repeticao.termino.data", nullValue())
        .body("repeticao.termino.quantidade", nullValue());

    consultar(token, "2026-11", "2027-03")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-11-30", "2026-12-30", "2027-01-30"))
        .body("itens.serie.numeroParcela", contains(3, 4, 5))
        .body("itens.serie.totalParcelas", contains(5, 5, 5));
  }

  @Test
  void ajusteDoUltimoDiaMantemAsDatasDeFimDeMes() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2026-03-31", repeticao("MENSAL", "DIA_DO_MES", indefinido())))
        .statusCode(201);

    consultar(token, "2026-03", "2026-07")
        .statusCode(200)
        .body(
            "itens.dataFinanceira",
            contains("2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30", "2026-07-31"));
  }

  @Test
  void ajusteDoUltimoDiaValeTambemComIntervaloMaiorQueUm() {
    var token = autenticar();
    criar(
            token,
            corpo("PLANEJADA", "2026-01-31", repeticao("MENSAL", 2, "DIA_DO_MES", indefinido())))
        .statusCode(201);

    consultar(token, "2026-01", "2026-07")
        .statusCode(200)
        .body(
            "itens.dataFinanceira",
            contains("2026-01-31", "2026-03-31", "2026-05-31", "2026-07-31"));
  }

  @Test
  void ajusteAnualDeFevereiroBissextoUsaUltimoDiaDoMes() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2028-02-29", repeticao("ANUAL", "DIA_DO_MES", indefinido())))
        .statusCode(201);

    consultar(token, "2028-02", "2028-02")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2028-02-29"));
    consultar(token, "2029-02", "2029-02")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2029-02-28"));
  }

  @Test
  void ajusteDoUltimoDiaComInicioNoDia29VoltaAoDia29ForaDeFevereiro() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2027-01-29", repeticao("MENSAL", "DIA_DO_MES", indefinido())))
        .statusCode(201);

    consultar(token, "2027-01", "2027-03")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2027-01-29", "2027-02-28", "2027-03-29"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("repeticoesComIntervaloMaximo")
  void intervaloMaximoProjetaSomenteAPrimeiraOcorrencia(
      String nome, String dataFinanceira, String repeticao) {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", dataFinanceira, repeticao))
        .statusCode(201)
        .body("repeticao.intervalo", equalTo(Integer.MAX_VALUE));

    consultar(token, "2027-01", "2027-12")
        .statusCode(200)
        .body("itens.dataFinanceira", contains(dataFinanceira));
  }

  static Stream<Arguments> repeticoesComIntervaloMaximo() {
    var maximo = Integer.MAX_VALUE;
    return Stream.of(
        Arguments.of(
            "mensal no dia do mes",
            "2027-01-10",
            repeticao("MENSAL", maximo, "DIA_DO_MES", indefinido())),
        Arguments.of(
            "anual no dia do mes",
            "2027-01-10",
            repeticao("ANUAL", maximo, "DIA_DO_MES", indefinido())),
        Arguments.of(
            "mensal no dia util",
            "2027-01-11",
            repeticao("MENSAL", maximo, "DIA_UTIL", indefinido())),
        Arguments.of(
            "diaria", "2027-01-10", repeticao("DIARIA", maximo, "DIA_DO_MES", indefinido())),
        Arguments.of(
            "semanal", "2027-01-10", repeticao("SEMANAL", maximo, "DIA_DO_MES", indefinido())));
  }

  @Test
  void semanalACadaDuasSemanasProjetaSomenteAsSemanasDoIntervalo() {
    var token = autenticar();
    criar(
            token,
            corpo("PLANEJADA", "2026-11-10", repeticao("SEMANAL", 2, "DIA_DO_MES", indefinido())))
        .statusCode(201)
        .body("repeticao.intervalo", equalTo(2));

    consultar(token, "2026-11", "2026-12")
        .statusCode(200)
        .body(
            "itens.dataFinanceira",
            contains("2026-11-10", "2026-11-24", "2026-12-08", "2026-12-22"));
  }

  @Test
  void diaUtilProjetaONesimoDiaUtilDeCadaMes() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2026-11-05", repeticao("MENSAL", "DIA_UTIL", indefinido())))
        .statusCode(201)
        .body("repeticao.ancoragem", equalTo("DIA_UTIL"))
        .body("repeticao.numeroDiaUtil", equalTo(4));

    consultar(token, "2026-11", "2027-01")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-11-05", "2026-12-04", "2027-01-06"));
  }

  @Test
  void diaUtilComIntervaloMaiorQueUmUsaOMesmoNumeroDeDiaUtil() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2026-11-05", repeticao("MENSAL", 2, "DIA_UTIL", indefinido())))
        .statusCode(201)
        .body("repeticao.numeroDiaUtil", equalTo(4));

    consultar(token, "2026-11", "2027-03")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-11-05", "2027-01-06", "2027-03-04"));
  }

  @Test
  void diaUtilNoVigesimoDiaUtilEhAceito() {
    criar(
            autenticar(),
            corpo("PLANEJADA", "2026-11-27", repeticao("MENSAL", "DIA_UTIL", indefinido())))
        .statusCode(201)
        .body("repeticao.numeroDiaUtil", equalTo(20));
  }

  @Test
  void diaUtilComParcelasNumeraAPartirDaDataFinanceira() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2026-11-05", repeticao("MENSAL", "DIA_UTIL", parcelas(1, 3))))
        .statusCode(201)
        .body("serie.numeroParcela", equalTo(1))
        .body("serie.totalParcelas", equalTo(3));

    consultar(token, "2026-11", "2027-03")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-11-05", "2026-12-04", "2027-01-06"))
        .body("itens.serie.numeroParcela", contains(1, 2, 3));
  }

  @Test
  void diariaSegueOsDiasConsecutivosAtravesDoFimDoMes() {
    var token = autenticar();
    criar(token, corpo("PLANEJADA", "2026-01-31", repeticao("DIARIA", "DIA_DO_MES", apos(3))))
        .statusCode(201);

    consultar(token, "2026-01", "2026-03")
        .statusCode(200)
        .body("itens.dataFinanceira", contains("2026-01-31", "2026-02-01", "2026-02-02"));
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
    var mensal = "MENSAL";
    return Stream.of(
        // Frequência e ancoragem
        Arguments.of(
            "dia util com frequencia diaria",
            terca,
            repeticao("DIARIA", "DIA_UTIL", indefinido()),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "dia util com frequencia semanal",
            terca,
            repeticao("SEMANAL", "DIA_UTIL", indefinido()),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "dia util com frequencia anual",
            terca,
            repeticao("ANUAL", "DIA_UTIL", indefinido()),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "dia util em data de fim de semana",
            "2026-11-28",
            repeticao(mensal, "DIA_UTIL", indefinido()),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "dia util que e o vigesimo primeiro do mes",
            "2026-11-30",
            repeticao(mensal, "DIA_UTIL", indefinido()),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "intervalo zero",
            terca,
            repeticao(mensal, 0, "DIA_DO_MES", indefinido()),
            422,
            "REPETICAO_INVALIDA"),
        // Término: campos de outro tipo e datas inválidas
        Arguments.of(
            "indefinido com data",
            terca,
            repeticao(mensal, "DIA_DO_MES", "{\"tipo\":\"INDEFINIDO\",\"data\":\"2027-01-10\"}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino em data anterior ao inicio",
            terca,
            repeticao(mensal, "DIA_DO_MES", emData("2026-11-01")),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino em data com quantidade",
            terca,
            repeticao(
                mensal,
                "DIA_DO_MES",
                "{\"tipo\":\"EM_DATA\",\"data\":\"2027-01-10\",\"quantidade\":3}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino apos zero ocorrencias",
            terca,
            repeticao(mensal, "DIA_DO_MES", apos(0)),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino apos ocorrencias com data",
            terca,
            repeticao(
                mensal,
                "DIA_DO_MES",
                "{\"tipo\":\"APOS_OCORRENCIAS\",\"quantidade\":2,\"data\":\"2027-01-10\"}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "termino apos ocorrencias com numeracao de parcelas",
            terca,
            repeticao(
                mensal,
                "DIA_DO_MES",
                "{\"tipo\":\"APOS_OCORRENCIAS\",\"quantidade\":2,"
                    + "\"numeroPrimeiraParcela\":1,\"totalParcelas\":2}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "parcelas com data",
            terca,
            repeticao(
                mensal,
                "DIA_DO_MES",
                "{\"tipo\":\"PARCELAS\",\"numeroPrimeiraParcela\":1,\"totalParcelas\":3,"
                    + "\"data\":\"2027-01-10\"}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "parcelas com quantidade",
            terca,
            repeticao(
                mensal,
                "DIA_DO_MES",
                "{\"tipo\":\"PARCELAS\",\"numeroPrimeiraParcela\":1,\"totalParcelas\":3,"
                    + "\"quantidade\":3}"),
            422,
            "REPETICAO_INVALIDA"),
        Arguments.of(
            "parcelas com primeira parcela zero",
            terca,
            repeticao(mensal, "DIA_DO_MES", parcelas(0, 3)),
            422,
            "PARCELAMENTO_INVALIDO"),
        Arguments.of(
            "parcelas com primeira parcela maior que o total",
            terca,
            repeticao(mensal, "DIA_DO_MES", parcelas(4, 3)),
            422,
            "PARCELAMENTO_INVALIDO"),
        // Contrato HTTP: campos ausentes, desconhecidos ou com valores fora do contrato
        Arguments.of("repeticao vazia", terca, "{}", 400, "DADOS_INVALIDOS"),
        Arguments.of(
            "termino ausente",
            terca,
            "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
                + "\"termino\":null}",
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "ancoragem ausente",
            terca,
            "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"termino\":{\"tipo\":\"INDEFINIDO\"}}",
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "intervalo ausente",
            terca,
            "{\"frequencia\":\"MENSAL\",\"ancoragem\":\"DIA_DO_MES\","
                + "\"termino\":{\"tipo\":\"INDEFINIDO\"}}",
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "ancoragem desconhecida",
            terca,
            repeticao(mensal, "ULTIMO_DIA_UTIL", indefinido()),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "tipo de termino desconhecido",
            terca,
            repeticao(mensal, "DIA_DO_MES", "{\"tipo\":\"XYZ\"}"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "termino em data sem data",
            terca,
            repeticao(mensal, "DIA_DO_MES", "{\"tipo\":\"EM_DATA\"}"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "termino apos ocorrencias sem quantidade",
            terca,
            repeticao(mensal, "DIA_DO_MES", "{\"tipo\":\"APOS_OCORRENCIAS\"}"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "parcelas sem total",
            terca,
            repeticao(mensal, "DIA_DO_MES", "{\"tipo\":\"PARCELAS\",\"numeroPrimeiraParcela\":1}"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "parcelas sem numeracao inicial",
            terca,
            repeticao(mensal, "DIA_DO_MES", "{\"tipo\":\"PARCELAS\",\"totalParcelas\":3}"),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "frequencia em ingles",
            terca,
            repeticao("DAILY", "DIA_DO_MES", indefinido()),
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "campo dias da semana",
            terca,
            "{\"frequencia\":\"SEMANAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
                + "\"diasSemana\":[\"TERCA\"],\"termino\":{\"tipo\":\"INDEFINIDO\"}}",
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "campo dias do mes",
            terca,
            "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
                + "\"diasMes\":[10],\"termino\":{\"tipo\":\"INDEFINIDO\"}}",
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "campo politica de datas",
            terca,
            "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
                + "\"politicaData\":\"PADRAO\",\"termino\":{\"tipo\":\"INDEFINIDO\"}}",
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "campo parcelamento",
            terca,
            "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
                + "\"parcelamento\":{\"numeroPrimeiraParcela\":1,\"totalParcelas\":3},"
                + "\"termino\":{\"tipo\":\"INDEFINIDO\"}}",
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "numero do dia util enviado pelo cliente",
            terca,
            "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
                + "\"numeroDiaUtil\":2,\"termino\":{\"tipo\":\"INDEFINIDO\"}}",
            400,
            "DADOS_INVALIDOS"),
        Arguments.of(
            "campo desconhecido na repeticao",
            terca,
            "{\"frequencia\":\"MENSAL\",\"intervalo\":1,\"ancoragem\":\"DIA_DO_MES\","
                + "\"termino\":{\"tipo\":\"INDEFINIDO\"},\"extra\":true}",
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

  private static String repeticao(String frequencia, String ancoragem, String termino) {
    return repeticao(frequencia, 1, ancoragem, termino);
  }

  private static String repeticao(
      String frequencia, int intervalo, String ancoragem, String termino) {
    return """
    {"frequencia":"%s","intervalo":%d,"ancoragem":"%s","termino":%s}
    """
        .formatted(frequencia, intervalo, ancoragem, termino);
  }

  private static String indefinido() {
    return "{\"tipo\":\"INDEFINIDO\"}";
  }

  private static String emData(String data) {
    return "{\"tipo\":\"EM_DATA\",\"data\":\"%s\"}".formatted(data);
  }

  private static String apos(int quantidade) {
    return "{\"tipo\":\"APOS_OCORRENCIAS\",\"quantidade\":%d}".formatted(quantidade);
  }

  private static String parcelas(int numeroPrimeiraParcela, int totalParcelas) {
    return "{\"tipo\":\"PARCELAS\",\"numeroPrimeiraParcela\":%d,\"totalParcelas\":%d}"
        .formatted(numeroPrimeiraParcela, totalParcelas);
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
