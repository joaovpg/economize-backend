package com.joaovpg.economize.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RecorrenciaMigrationTest {
  @Inject AgroalDataSource dataSource;

  @Test
  void migraEstruturaDeRecorrenciaEProtegeNumeracaoDoParcelamento() throws SQLException {
    var schema = "recorrencia_" + UUID.randomUUID().toString().replace("-", "");
    var ids = new Dados();
    try {
      flyway(schema, "5").migrate();
      inserirDadosLegados(schema, ids);
      flyway(schema, null).migrate();

      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        try {
          statement.execute("SET search_path TO \"" + schema + "\"");
          assertEquals(
              "RECORRENCIA",
              valor(
                  statement,
                  "SELECT STR_TIPO FROM TB004_GRUPO_RECORRENCIA WHERE ID_REGISTRO = '"
                      + ids.grupo
                      + "'"));
          assertEquals(
              1,
              quantidade(
                  statement,
                  "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '"
                      + schema
                      + "' AND TABLE_NAME = 'tb008_supressao_recorrencia'"));

          assertThrows(
              SQLException.class,
              () ->
                  statement.execute(
                      "UPDATE TB005_SEGMENTO_RECORRENCIA SET "
                          + "INT_NUMERO_PRIMEIRA_PARCELA = 4, "
                          + "INT_QUANTIDADE_TOTAL_ORIGINAL = 3 WHERE ID_REGISTRO = '"
                          + ids.segmento
                          + "'"));
        } finally {
          statement.execute("SET search_path TO public");
        }
      }
    } finally {
      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        statement.execute("SET search_path TO public");
      }
    }
  }

  private void inserirDadosLegados(String schema, Dados ids) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try {
        statement.execute("SET search_path TO \"" + schema + "\"");
        statement.execute(
            """
            INSERT INTO TB001_USUARIO (
                ID_REGISTRO, STR_NOME, STR_EMAIL, STR_SENHA_HASH, STR_TIMEZONE, STR_STATUS
            ) VALUES ('%s', 'Usuario', 'recorrencia-migration@example.com', 'hash',
                      'America/Sao_Paulo', 'ATIVO')
            """
                .formatted(ids.usuario));
        statement.execute(
            """
            INSERT INTO TB002_CONTA_FINANCEIRA (
                ID_REGISTRO, ID_USUARIO, STR_NOME, STR_MOEDA, DEC_SALDO_INICIAL,
                DAT_SALDO_INICIAL, BOL_ATIVO
            ) VALUES ('%s', '%s', 'Conta', 'BRL', 0, DATE '2026-01-01', TRUE)
            """
                .formatted(ids.conta, ids.usuario));
        statement.execute(
            """
            INSERT INTO TB004_GRUPO_RECORRENCIA (
                ID_REGISTRO, ID_USUARIO, STR_DESCRICAO, STR_STATUS
            ) VALUES ('%s', '%s', 'Recorrencia', 'ATIVO')
            """
                .formatted(ids.grupo, ids.usuario));
        statement.execute(
            """
            INSERT INTO TB005_SEGMENTO_RECORRENCIA (
                ID_REGISTRO, ID_USUARIO, ID_GRUPO_RECORRENCIA, ID_CONTA_FINANCEIRA,
                STR_TIPO_TRANSACAO, STR_DESCRICAO, DEC_VALOR, DAT_DTSTART, STR_RRULE,
                INT_TOTAL_OCORRENCIAS, STR_STATUS
            ) VALUES ('%s', '%s', '%s', '%s', 'DESPESA', 'Parcela', 10,
                      DATE '2026-01-10', 'FREQ=MONTHLY;BYMONTHDAY=10', NULL, 'ATIVO')
            """
                .formatted(ids.segmento, ids.usuario, ids.grupo, ids.conta));
      } finally {
        statement.execute("SET search_path TO public");
      }
    }
  }

  private int quantidade(java.sql.Statement statement, String sql) throws SQLException {
    try (var resultado = statement.executeQuery(sql)) {
      resultado.next();
      return resultado.getInt(1);
    }
  }

  private String valor(java.sql.Statement statement, String sql) throws SQLException {
    try (var resultado = statement.executeQuery(sql)) {
      resultado.next();
      return resultado.getString(1);
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
    private final UUID grupo = UUID.randomUUID();
    private final UUID segmento = UUID.randomUUID();
  }
}
