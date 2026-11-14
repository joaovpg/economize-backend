package com.joaovpg.economize.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RecorrenciaPoliticaRevisaoMigrationTest {
  private static final LocalDate HORIZONTE = LocalDate.of(2027, 12, 31);

  @Inject AgroalDataSource dataSource;
  @Inject ExpansorRecorrencia expansor;
  @Inject LeitorRruleRecorrencia leitorRrule;

  @Test
  void preencherPoliticaPeloTipoDoGrupoPreservandoOcorrenciasSupressoesEVersoes()
      throws SQLException {
    var schema = "politica_" + UUID.randomUUID().toString().replace("-", "");
    var ids = new Dados();
    try {
      flyway(schema, "7").migrate();
      inserirDadosLegados(schema, ids);
      var antes = capturar(schema, false);

      flyway(schema, null).migrate();
      var depois = capturar(schema, true);

      assertEquals(antes.segmentos(), depois.segmentos());
      assertEquals(antes.supressoes(), depois.supressoes());
      assertEquals(antes.transacoes(), depois.transacoes());
      assertEquals(antes.ocorrencias(), depois.ocorrencias());
      assertTrue(
          depois.ocorrencias().contains(ids.segmentoParcela + "@2026-02-28#2=100.0000"),
          "a parcela de fevereiro deve continuar ajustada ao ultimo dia do mes");

      var politicas =
          consultar(
                  schema,
                  "SELECT ID_REGISTRO, STR_POLITICA_DATA FROM TB005_SEGMENTO_RECORRENCIA",
                  2)
              .stream()
              .map(linha -> linha.split("\\|"))
              .collect(Collectors.toMap(partes -> partes[0], partes -> partes[1]));
      assertEquals(
          Map.of(
              ids.segmentoRecorrente.toString(), "PADRAO",
              ids.segmentoConcluido.toString(), "PADRAO",
              ids.segmentoAtual.toString(), "PADRAO",
              ids.segmentoParcela.toString(), "AJUSTAR_ULTIMO_DIA_MES"),
          politicas);
      assertEquals(
          List.of("0"),
          consultar(schema, "SELECT DISTINCT VER_REVISAO_SERIE FROM TB004_GRUPO_RECORRENCIA", 1));
      assertThrows(
          SQLException.class,
          () ->
              executar(
                  schema, "UPDATE TB005_SEGMENTO_RECORRENCIA SET STR_POLITICA_DATA = 'INVALIDA'"));
    } finally {
      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        statement.execute("SET search_path TO public");
      }
    }
  }

  private Captura capturar(String schema, boolean migrado) throws SQLException {
    var politica =
        migrado
            ? "S.STR_POLITICA_DATA"
            : """
            CASE WHEN G.STR_TIPO = 'PARCELAMENTO'
                 THEN 'AJUSTAR_ULTIMO_DIA_MES' ELSE 'PADRAO' END
            """;
    return new Captura(
        consultar(
            schema,
            """
            SELECT ID_REGISTRO, ID_GRUPO_RECORRENCIA, STR_RRULE, DAT_DTSTART, DAT_FIM,
                   INT_TOTAL_OCORRENCIAS, INT_NUMERO_PRIMEIRA_PARCELA,
                   INT_QUANTIDADE_TOTAL_ORIGINAL, DEC_VALOR, STR_STATUS
            FROM TB005_SEGMENTO_RECORRENCIA
            ORDER BY ID_REGISTRO
            """,
            10),
        consultar(
            schema,
            """
            SELECT ID_SEGMENTO_RECORRENCIA, DAT_IDENTIFICADOR_RECORRENCIA
            FROM TB008_SUPRESSAO_RECORRENCIA
            ORDER BY ID_SEGMENTO_RECORRENCIA, DAT_IDENTIFICADOR_RECORRENCIA
            """,
            2),
        consultar(
            schema,
            """
            SELECT ID_REGISTRO, ID_SEGMENTO_RECORRENCIA, DAT_FINANCEIRA,
                   DAT_IDENTIFICADOR_RECORRENCIA, DEC_VALOR, STR_SITUACAO,
                   DHR_EFETIVACAO, BOL_EXCECAO_RECORRENCIA
            FROM TB006_TRANSACAO
            ORDER BY ID_REGISTRO
            """,
            8),
        ocorrencias(
            schema,
            """
            SELECT S.ID_REGISTRO, S.STR_RRULE, S.DAT_DTSTART, S.DAT_FIM,\
            """
                + politica
                + """
                     , S.DEC_VALOR
                FROM TB005_SEGMENTO_RECORRENCIA S
                JOIN TB004_GRUPO_RECORRENCIA G ON G.ID_REGISTRO = S.ID_GRUPO_RECORRENCIA
                ORDER BY S.ID_REGISTRO
                """));
  }

  private List<String> ocorrencias(String schema, String sql) throws SQLException {
    var resultado = new ArrayList<String>();
    for (var linha : consultar(schema, sql, 6)) {
      var colunas = linha.split("\\|", -1);
      var inicio = LocalDate.parse(colunas[2]);
      var fim = colunas[3].equals("null") ? HORIZONTE : LocalDate.parse(colunas[3]);
      if (fim.isAfter(HORIZONTE)) {
        fim = HORIZONTE;
      }
      var regra = leitorRrule.lerSegmento(inicio, colunas[1]);
      var politica = PoliticaDataOcorrencia.valueOf(colunas[4]);
      for (var ocorrencia : expansor.expandir(regra, inicio, fim, politica)) {
        resultado.add(
            colunas[0]
                + "@"
                + ocorrencia.dataOriginal()
                + "#"
                + ocorrencia.numeroOcorrencia()
                + "="
                + colunas[5]);
      }
    }
    return resultado;
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

  private void inserirDadosLegados(String schema, Dados ids) throws SQLException {
    executar(
        schema,
        """
        INSERT INTO TB001_USUARIO (
            ID_REGISTRO, STR_NOME, STR_EMAIL, STR_SENHA_HASH, STR_TIMEZONE, BOL_ATIVO
        ) VALUES ('%1$s', 'Usuario', 'politica-migration@example.com', 'hash',
                  'America/Sao_Paulo', TRUE);
        INSERT INTO TB002_CONTA_FINANCEIRA (
            ID_REGISTRO, ID_USUARIO, STR_NOME, STR_MOEDA, DEC_SALDO_INICIAL,
            DAT_SALDO_INICIAL, BOL_ATIVO
        ) VALUES ('%2$s', '%1$s', 'Conta', 'BRL', 100, DATE '2026-01-01', TRUE);
        INSERT INTO TB004_GRUPO_RECORRENCIA (
            ID_REGISTRO, ID_USUARIO, STR_DESCRICAO, STR_STATUS, STR_TIPO
        ) VALUES ('%3$s', '%1$s', 'Aluguel', 'ATIVO', 'RECORRENCIA'),
                 ('%4$s', '%1$s', 'Academia', 'ATIVO', 'RECORRENCIA'),
                 ('%5$s', '%1$s', 'Notebook', 'ATIVO', 'PARCELAMENTO');
        INSERT INTO TB005_SEGMENTO_RECORRENCIA (
            ID_REGISTRO, ID_USUARIO, ID_GRUPO_RECORRENCIA, ID_CONTA_FINANCEIRA,
            STR_TIPO_TRANSACAO, STR_DESCRICAO, DEC_VALOR, DAT_DTSTART, DAT_FIM, STR_RRULE,
            INT_TOTAL_OCORRENCIAS, INT_NUMERO_PRIMEIRA_PARCELA,
            INT_QUANTIDADE_TOTAL_ORIGINAL, STR_STATUS
        ) VALUES
            ('%6$s', '%1$s', '%3$s', '%2$s', 'DESPESA', 'Aluguel', 50,
             DATE '2026-01-10', NULL, 'FREQ=MONTHLY;BYMONTHDAY=10', NULL, NULL, NULL, 'ATIVO'),
            ('%7$s', '%1$s', '%4$s', '%2$s', 'DESPESA', 'Academia', 30,
             DATE '2026-01-05', DATE '2026-05-31', 'FREQ=WEEKLY;BYDAY=MO', NULL, NULL, NULL,
             'CONCLUIDO'),
            ('%8$s', '%1$s', '%4$s', '%2$s', 'DESPESA', 'Academia', 35,
             DATE '2026-06-01', NULL, 'FREQ=WEEKLY;BYDAY=MO;COUNT=10', 10, NULL, NULL,
             'ATIVO'),
            ('%9$s', '%1$s', '%5$s', '%2$s', 'DESPESA', 'Notebook', 100,
             DATE '2026-01-31', NULL, 'FREQ=MONTHLY;COUNT=3', 3, 1, 3, 'ATIVO');
        INSERT INTO TB006_TRANSACAO (
            ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, ID_GRUPO_RECORRENCIA,
            ID_SEGMENTO_RECORRENCIA, STR_TIPO, STR_SITUACAO, STR_DESCRICAO, DEC_VALOR,
            DAT_FINANCEIRA, DAT_IDENTIFICADOR_RECORRENCIA, DHR_EFETIVACAO,
            BOL_EXCECAO_RECORRENCIA
        ) VALUES
            ('%10$s', '%1$s', '%2$s', '%3$s', '%6$s', 'DESPESA', 'PLANEJADA', 'Aluguel', 70,
             DATE '2026-03-12', DATE '2026-03-10', NULL, TRUE),
            ('%11$s', '%1$s', '%2$s', '%5$s', '%9$s', 'DESPESA', 'EFETIVADA', 'Notebook', 100,
             DATE '2026-01-31', DATE '2026-01-31', TIMESTAMPTZ '2026-01-31 12:00:00+00', TRUE);
        INSERT INTO TB008_SUPRESSAO_RECORRENCIA (
            ID_REGISTRO, ID_USUARIO, ID_GRUPO_RECORRENCIA, ID_SEGMENTO_RECORRENCIA,
            DAT_IDENTIFICADOR_RECORRENCIA
        ) VALUES ('%12$s', '%1$s', '%3$s', '%6$s', DATE '2026-02-10');
        """
            .formatted(
                ids.usuario,
                ids.conta,
                ids.grupoRecorrente,
                ids.grupoAcademia,
                ids.grupoParcela,
                ids.segmentoRecorrente,
                ids.segmentoConcluido,
                ids.segmentoAtual,
                ids.segmentoParcela,
                ids.transacaoExcecao,
                ids.transacaoParcela,
                ids.supressao));
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

  private record Captura(
      List<String> segmentos,
      List<String> supressoes,
      List<String> transacoes,
      List<String> ocorrencias) {}

  private static class Dados {
    private final UUID usuario = UUID.randomUUID();
    private final UUID conta = UUID.randomUUID();
    private final UUID grupoRecorrente = UUID.randomUUID();
    private final UUID grupoAcademia = UUID.randomUUID();
    private final UUID grupoParcela = UUID.randomUUID();
    private final UUID segmentoRecorrente = UUID.randomUUID();
    private final UUID segmentoConcluido = UUID.randomUUID();
    private final UUID segmentoAtual = UUID.randomUUID();
    private final UUID segmentoParcela = UUID.randomUUID();
    private final UUID transacaoExcecao = UUID.randomUUID();
    private final UUID transacaoParcela = UUID.randomUUID();
    private final UUID supressao = UUID.randomUUID();
  }
}
