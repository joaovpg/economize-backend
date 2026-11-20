package com.joaovpg.economize.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.joaovpg.economize.recorrencia.domain.ExpansorRecorrencia;
import com.joaovpg.economize.recorrencia.domain.LeitorRruleRecorrencia;
import com.joaovpg.economize.recorrencia.domain.PoliticaDataOcorrencia;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
class RepeticaoCanonicaMigrationTest {
  private static final LocalDate JANELA_INICIO = LocalDate.of(2026, 1, 1);
  private static final LocalDate JANELA_FIM = LocalDate.of(2026, 3, 31);

  @Inject AgroalDataSource dataSource;
  @Inject ExpansorRecorrencia expansor;
  @Inject LeitorRruleRecorrencia leitorRrule;

  @Test
  void normalizaSegmentosRepresentaveisSemMudarDatasNemRegistrosFinanceiros() throws SQLException {
    var schema = nomeSchema();
    var ids = new Dados();
    try {
      flyway(schema, "10").migrate();
      inserirSegmentosRepresentaveis(schema, ids);
      var financeiroAntes = financeiro(schema);

      flyway(schema, null).migrate();

      assertEquals(financeiroAntes, financeiro(schema));
      assertEquals(
          Map.of(
              ids.mensalDiaDez.toString(), "FREQ=MONTHLY|AJUSTAR_ULTIMO_DIA_MES",
              ids.semanalSegunda.toString(), "FREQ=WEEKLY;BYDAY=MO|PADRAO",
              ids.parcelamentoMensal.toString(), "FREQ=MONTHLY;COUNT=3|AJUSTAR_ULTIMO_DIA_MES",
              ids.anualMarco.toString(), "FREQ=YEARLY|AJUSTAR_ULTIMO_DIA_MES",
              ids.mensalCortado.toString(), "FREQ=MONTHLY|AJUSTAR_ULTIMO_DIA_MES"),
          canonicos(schema));

      // As datas esperadas são fixas, calculadas à mão antes da migração; o motor atual
      // deve projetar exatamente as mesmas datas a partir das colunas migradas.
      assertEquals(
          Map.of(
              ids.mensalDiaDez.toString(),
              List.of("2026-01-10", "2026-02-10", "2026-03-10"),
              ids.semanalSegunda.toString(),
              List.of(
                  "2026-01-05",
                  "2026-01-12",
                  "2026-01-19",
                  "2026-01-26",
                  "2026-02-02",
                  "2026-02-09",
                  "2026-02-16",
                  "2026-02-23",
                  "2026-03-02",
                  "2026-03-09",
                  "2026-03-16",
                  "2026-03-23",
                  "2026-03-30"),
              ids.parcelamentoMensal.toString(),
              List.of("2026-01-10", "2026-02-10", "2026-03-10"),
              ids.anualMarco.toString(),
              List.of("2026-03-15"),
              ids.mensalCortado.toString(),
              List.of("2026-01-10", "2026-02-10", "2026-03-10")),
          projetar(schema));
    } finally {
      descartar(schema);
    }
  }

  static Stream<Arguments> segmentosBloqueantes() {
    return Stream.of(
        Arguments.of(
            "mensal no dia 31 com politica padrao muda datas",
            "RECORRENCIA",
            "FREQ=MONTHLY;BYMONTHDAY=31",
            LocalDate.of(2026, 1, 31),
            "PADRAO"),
        Arguments.of(
            "parcelamento mensal no dia 31 com BYMONTHDAY e ajuste muda datas",
            "PARCELAMENTO",
            "FREQ=MONTHLY;BYMONTHDAY=31;COUNT=3",
            LocalDate.of(2026, 1, 31),
            "AJUSTAR_ULTIMO_DIA_MES"),
        Arguments.of(
            "anual em 29 de fevereiro com politica padrao muda datas",
            "RECORRENCIA",
            "FREQ=YEARLY",
            LocalDate.of(2028, 2, 29),
            "PADRAO"),
        Arguments.of(
            "semanal com dois dias da semana nao e representavel",
            "RECORRENCIA",
            "FREQ=WEEKLY;BYDAY=MO,TU",
            LocalDate.of(2026, 1, 5),
            "PADRAO"),
        Arguments.of(
            "posicao no mes nao e representavel",
            "RECORRENCIA",
            "FREQ=MONTHLY;BYDAY=TU;BYSETPOS=2",
            LocalDate.of(2026, 1, 13),
            "PADRAO"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("segmentosBloqueantes")
  void bloqueiaAMigracaoSemAlterarNadaQuandoExisteSegmentoBloqueante(
      String nome, String tipoGrupo, String rrule, LocalDate inicio, String politica)
      throws SQLException {
    var schema = nomeSchema();
    var ids = new Dados();
    try {
      flyway(schema, "10").migrate();
      inserirSegmentoLegado(schema, ids, tipoGrupo, rrule, inicio, politica);
      var antes = canonicos(schema);

      assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());

      assertEquals(Map.of(ids.segmentoUnico.toString(), rrule + "|" + politica), antes);
      assertEquals(antes, canonicos(schema));
    } finally {
      descartar(schema);
    }
  }

  private Map<String, String> canonicos(String schema) throws SQLException {
    var mapa = new TreeMap<String, String>();
    var sql = "SELECT ID_REGISTRO, STR_RRULE, STR_POLITICA_DATA FROM TB005_SEGMENTO_RECORRENCIA";
    for (var linha : consultar(schema, sql, 3)) {
      var colunas = linha.split("\\|", -1);
      mapa.put(colunas[0], colunas[1] + "|" + colunas[2]);
    }
    return mapa;
  }

  private List<String> financeiro(String schema) throws SQLException {
    var linhas = new ArrayList<String>();
    linhas.addAll(
        consultar(
            schema,
            """
            SELECT ID_REGISTRO, ID_GRUPO_RECORRENCIA, DAT_DTSTART, DAT_FIM, INT_TOTAL_OCORRENCIAS,
                   INT_NUMERO_PRIMEIRA_PARCELA, INT_QUANTIDADE_TOTAL_ORIGINAL, DEC_VALOR, STR_STATUS
            FROM TB005_SEGMENTO_RECORRENCIA
            ORDER BY ID_REGISTRO
            """,
            9));
    linhas.addAll(
        consultar(
            schema,
            """
            SELECT ID_REGISTRO, ID_SEGMENTO_RECORRENCIA, DAT_FINANCEIRA,
                   DAT_IDENTIFICADOR_RECORRENCIA, DEC_VALOR, STR_SITUACAO, BOL_EXCECAO_RECORRENCIA
            FROM TB006_TRANSACAO
            ORDER BY ID_REGISTRO
            """,
            7));
    linhas.addAll(
        consultar(
            schema,
            """
            SELECT ID_SEGMENTO_RECORRENCIA, DAT_IDENTIFICADOR_RECORRENCIA
            FROM TB008_SUPRESSAO_RECORRENCIA
            ORDER BY ID_SEGMENTO_RECORRENCIA, DAT_IDENTIFICADOR_RECORRENCIA
            """,
            2));
    return linhas;
  }

  private Map<String, List<String>> projetar(String schema) throws SQLException {
    var projecao = new TreeMap<String, List<String>>();
    for (var linha :
        consultar(
            schema,
            """
            SELECT ID_REGISTRO, STR_RRULE, DAT_DTSTART, DAT_FIM, STR_POLITICA_DATA
            FROM TB005_SEGMENTO_RECORRENCIA
            ORDER BY ID_REGISTRO
            """,
            5)) {
      var colunas = linha.split("\\|", -1);
      var inicio = LocalDate.parse(colunas[2]);
      var fim = colunas[3].equals("null") ? JANELA_FIM : LocalDate.parse(colunas[3]);
      if (fim.isAfter(JANELA_FIM)) {
        fim = JANELA_FIM;
      }
      var regra = leitorRrule.ler(inicio, colunas[1]);
      var datas = new ArrayList<String>();
      for (var ocorrencia :
          expansor.expandir(
              regra, JANELA_INICIO, fim, PoliticaDataOcorrencia.valueOf(colunas[4]))) {
        datas.add(ocorrencia.dataOriginal().toString());
      }
      projecao.put(colunas[0], datas);
    }
    return projecao;
  }

  private void inserirSegmentosRepresentaveis(String schema, Dados ids) throws SQLException {
    inserirUsuarioEConta(schema, ids);
    executar(
        schema,
        """
        INSERT INTO TB004_GRUPO_RECORRENCIA (ID_REGISTRO, ID_USUARIO, STR_DESCRICAO, STR_STATUS, STR_TIPO)
        VALUES ('%1$s', '%3$s', 'Aluguel', 'ATIVO', 'RECORRENCIA'),
               ('%2$s', '%3$s', 'Academia', 'ATIVO', 'RECORRENCIA'),
               ('%4$s', '%3$s', 'Notebook', 'ATIVO', 'PARCELAMENTO'),
               ('%5$s', '%3$s', 'Seguro', 'ATIVO', 'RECORRENCIA');
        INSERT INTO TB005_SEGMENTO_RECORRENCIA (
            ID_REGISTRO, ID_USUARIO, ID_GRUPO_RECORRENCIA, ID_CONTA_FINANCEIRA,
            STR_TIPO_TRANSACAO, STR_DESCRICAO, DEC_VALOR, DAT_DTSTART, DAT_FIM, STR_RRULE,
            INT_TOTAL_OCORRENCIAS, INT_NUMERO_PRIMEIRA_PARCELA, INT_QUANTIDADE_TOTAL_ORIGINAL,
            STR_POLITICA_DATA, STR_STATUS
        ) VALUES
            ('%6$s', '%3$s', '%1$s', '%7$s', 'DESPESA', 'Aluguel', 50, DATE '2026-01-10', NULL,
             'FREQ=MONTHLY;BYMONTHDAY=10', NULL, NULL, NULL, 'PADRAO', 'ATIVO'),
            ('%8$s', '%3$s', '%2$s', '%7$s', 'DESPESA', 'Academia', 30, DATE '2026-01-05', NULL,
             'FREQ=WEEKLY;BYDAY=MO', NULL, NULL, NULL, 'PADRAO', 'ATIVO'),
            ('%9$s', '%3$s', '%4$s', '%7$s', 'DESPESA', 'Notebook', 100, DATE '2026-01-10', NULL,
             'FREQ=MONTHLY;BYMONTHDAY=10;COUNT=3', 3, 1, 3, 'AJUSTAR_ULTIMO_DIA_MES', 'ATIVO'),
            ('%10$s', '%3$s', '%5$s', '%7$s', 'DESPESA', 'Seguro', 80, DATE '2026-03-15', NULL,
             'FREQ=YEARLY', NULL, NULL, NULL, 'PADRAO', 'ATIVO'),
            ('%11$s', '%3$s', '%5$s', '%7$s', 'DESPESA', 'Seguro cortado', 20, DATE '2026-01-10',
             DATE '2026-03-20', 'FREQ=MONTHLY;BYMONTHDAY=10', NULL, NULL, NULL, 'PADRAO',
             'ATIVO');
        INSERT INTO TB006_TRANSACAO (
            ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, ID_GRUPO_RECORRENCIA,
            ID_SEGMENTO_RECORRENCIA, STR_TIPO, STR_SITUACAO, STR_DESCRICAO, DEC_VALOR,
            DAT_FINANCEIRA, DAT_IDENTIFICADOR_RECORRENCIA, DHR_EFETIVACAO,
            BOL_EXCECAO_RECORRENCIA
        ) VALUES
            ('%12$s', '%3$s', '%7$s', '%5$s', '%11$s', 'DESPESA', 'PLANEJADA', 'Seguro cortado',
             20, DATE '2026-02-10', DATE '2026-02-10', NULL, TRUE);
        INSERT INTO TB008_SUPRESSAO_RECORRENCIA (
            ID_REGISTRO, ID_USUARIO, ID_GRUPO_RECORRENCIA, ID_SEGMENTO_RECORRENCIA,
            DAT_IDENTIFICADOR_RECORRENCIA
        ) VALUES ('%13$s', '%3$s', '%5$s', '%11$s', DATE '2026-03-10');
        """
            .formatted(
                ids.grupoAluguel,
                ids.grupoAcademia,
                ids.usuario,
                ids.grupoNotebook,
                ids.grupoSeguro,
                ids.mensalDiaDez,
                ids.conta,
                ids.semanalSegunda,
                ids.parcelamentoMensal,
                ids.anualMarco,
                ids.mensalCortado,
                ids.transacaoExcecao,
                ids.supressao));
  }

  private void inserirSegmentoLegado(
      String schema, Dados ids, String tipoGrupo, String rrule, LocalDate inicio, String politica)
      throws SQLException {
    inserirUsuarioEConta(schema, ids);
    executar(
        schema,
        """
        INSERT INTO TB004_GRUPO_RECORRENCIA (ID_REGISTRO, ID_USUARIO, STR_DESCRICAO, STR_STATUS, STR_TIPO)
        VALUES ('%1$s', '%2$s', 'Bloqueante', 'ATIVO', '%8$s');
        INSERT INTO TB005_SEGMENTO_RECORRENCIA (
            ID_REGISTRO, ID_USUARIO, ID_GRUPO_RECORRENCIA, ID_CONTA_FINANCEIRA,
            STR_TIPO_TRANSACAO, STR_DESCRICAO, DEC_VALOR, DAT_DTSTART, STR_RRULE,
            STR_POLITICA_DATA, STR_STATUS
        ) VALUES ('%3$s', '%2$s', '%1$s', '%4$s', 'DESPESA', 'Bloqueante', 10,
                  DATE '%5$s', '%6$s', '%7$s', 'ATIVO');
        """
            .formatted(
                ids.grupoAluguel,
                ids.usuario,
                ids.segmentoUnico,
                ids.conta,
                inicio,
                rrule,
                politica,
                tipoGrupo));
  }

  private void inserirUsuarioEConta(String schema, Dados ids) throws SQLException {
    executar(
        schema,
        """
        INSERT INTO TB001_USUARIO (ID_REGISTRO, STR_NOME, STR_EMAIL, STR_SENHA_HASH,
            STR_TIMEZONE, BOL_ATIVO)
        VALUES ('%1$s', 'Usuario', 'repeticao-canonica@example.com', 'hash',
            'America/Sao_Paulo', TRUE);
        INSERT INTO TB002_CONTA_FINANCEIRA (ID_REGISTRO, ID_USUARIO, STR_NOME, STR_MOEDA,
            DEC_SALDO_INICIAL, DAT_SALDO_INICIAL, BOL_ATIVO)
        VALUES ('%2$s', '%1$s', 'Conta', 'BRL', 100, DATE '2026-01-01', TRUE);
        """
            .formatted(ids.usuario, ids.conta));
  }

  private String nomeSchema() {
    return "canonica_" + UUID.randomUUID().toString().replace("-", "");
  }

  private void descartar(String schema) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
      statement.execute("SET search_path TO public");
    }
  }

  private List<String> consultar(String schema, String sql, int colunas) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try {
        statement.execute("SET search_path TO \"" + schema + "\"");
        try (var resultado = statement.executeQuery(sql)) {
          var linhas = new ArrayList<String>();
          while (resultado.next()) {
            var partes = new ArrayList<String>();
            for (var indice = 1; indice <= colunas; indice++) {
              partes.add(String.valueOf(resultado.getObject(indice)));
            }
            linhas.add(String.join("|", partes));
          }
          return linhas;
        }
      } finally {
        statement.execute("SET search_path TO public");
      }
    }
  }

  private void executar(String schema, String sql) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try {
        statement.execute("SET search_path TO \"" + schema + "\"");
        statement.execute(sql);
      } finally {
        statement.execute("SET search_path TO public");
      }
    }
  }

  private Flyway flyway(String schema, String target) {
    var configuracao =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration");
    if (target != null) {
      configuracao.target(target);
    }
    return configuracao.load();
  }

  private static class Dados {
    private final UUID usuario = UUID.randomUUID();
    private final UUID conta = UUID.randomUUID();
    private final UUID grupoAluguel = UUID.randomUUID();
    private final UUID grupoAcademia = UUID.randomUUID();
    private final UUID grupoNotebook = UUID.randomUUID();
    private final UUID grupoSeguro = UUID.randomUUID();
    private final UUID mensalDiaDez = UUID.randomUUID();
    private final UUID semanalSegunda = UUID.randomUUID();
    private final UUID parcelamentoMensal = UUID.randomUUID();
    private final UUID anualMarco = UUID.randomUUID();
    private final UUID mensalCortado = UUID.randomUUID();
    private final UUID segmentoUnico = UUID.randomUUID();
    private final UUID transacaoExcecao = UUID.randomUUID();
    private final UUID supressao = UUID.randomUUID();
  }
}
