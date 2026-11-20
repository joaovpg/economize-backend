package com.joaovpg.economize.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransacaoMigrationTest {
  @Inject AgroalDataSource dataSource;

  @Test
  void renomeiaColunasERemoveHistoricoCancelado() throws SQLException {
    var schema = "transacao_" + UUID.randomUUID().toString().replace("-", "");
    var ids = new DadosLegados();

    try {
      flyway(schema, "2").migrate();
      inserirDadosLegados(schema, ids);
      flyway(schema, null).migrate();

      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        try {
          statement.execute("SET search_path TO \"" + schema + "\"");
          try (var colunas =
              statement.executeQuery(
                  """
                  SELECT COLUMN_NAME
                  FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'tb006_transacao'
                  """
                      .formatted(schema))) {
            var nomes = new java.util.HashSet<String>();
            while (colunas.next()) {
              nomes.add(colunas.getString(1).toUpperCase());
            }
            assertTrue(nomes.contains("STR_SITUACAO"));
            assertTrue(nomes.contains("DAT_FINANCEIRA"));
            assertFalse(nomes.contains("STR_STATUS"));
            assertFalse(nomes.contains("DAT_VENCIMENTO"));
          }

          assertEquals(1, quantidade(statement, "TB006_TRANSACAO"));
          assertEquals(0, quantidade(statement, "TB007_TRANSFERENCIA"));
          assertEquals(
              "PLANEJADA",
              valor(
                  statement,
                  "SELECT STR_SITUACAO FROM TB006_TRANSACAO WHERE ID_REGISTRO = '"
                      + ids.planejada
                      + "'"));
          assertEquals(
              1,
              quantidade(
                  statement,
                  "PG_INDEXES WHERE SCHEMANAME = '"
                      + schema
                      + "' AND INDEXNAME = 'ix006_01_conta_data_financeira'"));
          assertEquals(
              1,
              quantidade(
                  statement,
                  "PG_INDEXES WHERE SCHEMANAME = '"
                      + schema
                      + "' AND INDEXNAME = 'ix006_02_usuario_situacao_data_financeira'"));
          assertEquals(
              1,
              quantidade(
                  statement,
                  "PG_INDEXES WHERE SCHEMANAME = '"
                      + schema
                      + "' AND INDEXNAME = 'ix006_03_usuario_data_financeira'"));
          assertEquals(
              1,
              quantidade(
                  statement,
                  "PG_CONSTRAINT WHERE CONNAME = 'ck006_02_situacao'"
                      + " AND CONRELID = 'TB006_TRANSACAO'::REGCLASS"));

          var funcaoConta =
              valor(
                  statement,
                  "SELECT PG_GET_FUNCTIONDEF('FN006_VALIDAR_CONTA_TRANSACAO()'::REGPROCEDURE)");
          assertTrue(funcaoConta.contains("NEW.DAT_FINANCEIRA"));
          assertFalse(funcaoConta.contains("NEW.DAT_VENCIMENTO"));
          var funcaoTransferencia =
              valor(
                  statement,
                  "SELECT PG_GET_FUNCTIONDEF('FN007_VERIFICAR_TRANSFERENCIA(UUID)'::REGPROCEDURE)");
          assertTrue(funcaoTransferencia.contains("SAIDA.STR_SITUACAO"));
          assertTrue(funcaoTransferencia.contains("SAIDA.DAT_FINANCEIRA"));

          assertThrows(
              SQLException.class,
              () ->
                  statement.execute(
                      """
                      INSERT INTO TB006_TRANSACAO (
                          ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, STR_TIPO, STR_SITUACAO,
                          STR_DESCRICAO, DEC_VALOR, DAT_FINANCEIRA
                      ) VALUES ('%s', '%s', '%s', 'DESPESA', 'CANCELADA', 'Invalida', 1, DATE '2026-01-01')
                      """
                          .formatted(UUID.randomUUID(), ids.usuario, ids.contaOrigem)));
          assertThrows(
              SQLException.class,
              () ->
                  statement.execute(
                      """
                      INSERT INTO TB006_TRANSACAO (
                          ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, STR_TIPO, STR_SITUACAO,
                          STR_DESCRICAO, DEC_VALOR, DAT_FINANCEIRA
                      ) VALUES ('%s', '%s', '%s', 'DESPESA', 'PLANEJADA', 'Anterior', 1, DATE '2025-12-31')
                      """
                          .formatted(UUID.randomUUID(), ids.usuario, ids.contaOrigem)));
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

  private void inserirDadosLegados(String schema, DadosLegados ids) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try {
        statement.execute("SET search_path TO \"" + schema + "\"");
        statement.execute(
            """
            INSERT INTO TB001_USUARIO (
                ID_REGISTRO, STR_NOME, STR_EMAIL, STR_SENHA_HASH, STR_TIMEZONE, STR_STATUS
            ) VALUES ('%s', 'Usuario', 'transacao@example.com', 'hash', 'America/Sao_Paulo', 'ATIVO')
            """
                .formatted(ids.usuario));
        statement.execute(
            """
            INSERT INTO TB002_CONTA_FINANCEIRA (
                ID_REGISTRO, ID_USUARIO, STR_NOME, STR_MOEDA, DEC_SALDO_INICIAL,
                DAT_SALDO_INICIAL, BOL_ATIVO
            ) VALUES
                ('%s', '%s', 'Origem', 'BRL', 0, DATE '2026-01-01', TRUE),
                ('%s', '%s', 'Destino', 'BRL', 0, DATE '2026-01-01', TRUE)
            """
                .formatted(ids.contaOrigem, ids.usuario, ids.contaDestino, ids.usuario));
        statement.execute("SET CONSTRAINTS ALL DEFERRED");
        statement.execute(
            """
            INSERT INTO TB006_TRANSACAO (
                ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, STR_TIPO, STR_STATUS,
                STR_DESCRICAO, DEC_VALOR, DAT_VENCIMENTO
            ) VALUES
                ('%s', '%s', '%s', 'DESPESA', 'PLANEJADA', 'Mantida', 10, DATE '2026-01-01'),
                ('%s', '%s', '%s', 'DESPESA', 'CANCELADA', 'Cancelada', 10, DATE '2026-01-01'),
                ('%s', '%s', '%s', 'TRANSFERENCIA', 'CANCELADA', 'Saida', 10, DATE '2026-01-01'),
                ('%s', '%s', '%s', 'TRANSFERENCIA', 'CANCELADA', 'Entrada', 10, DATE '2026-01-01')
            """
                .formatted(
                    ids.planejada,
                    ids.usuario,
                    ids.contaOrigem,
                    ids.cancelada,
                    ids.usuario,
                    ids.contaOrigem,
                    ids.saida,
                    ids.usuario,
                    ids.contaOrigem,
                    ids.entrada,
                    ids.usuario,
                    ids.contaDestino));
        statement.execute(
            """
            INSERT INTO TB007_TRANSFERENCIA (
                ID_REGISTRO, ID_USUARIO, ID_CONTA_ORIGEM, ID_CONTA_DESTINO,
                ID_TRANSACAO_SAIDA, ID_TRANSACAO_ENTRADA, STR_STATUS, STR_DESCRICAO,
                DEC_VALOR, DAT_TRANSFERENCIA
            ) VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 'CANCELADA',
                      'Cancelada', 10, DATE '2026-01-01')
            """
                .formatted(
                    ids.transferencia,
                    ids.usuario,
                    ids.contaOrigem,
                    ids.contaDestino,
                    ids.saida,
                    ids.entrada));
      } finally {
        statement.execute("SET search_path TO public");
      }
    }
  }

  private int quantidade(java.sql.Statement statement, String tabela) throws SQLException {
    try (var resultado = statement.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
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

  private static class DadosLegados {
    private final UUID usuario = UUID.randomUUID();
    private final UUID contaOrigem = UUID.randomUUID();
    private final UUID contaDestino = UUID.randomUUID();
    private final UUID planejada = UUID.randomUUID();
    private final UUID cancelada = UUID.randomUUID();
    private final UUID saida = UUID.randomUUID();
    private final UUID entrada = UUID.randomUUID();
    private final UUID transferencia = UUID.randomUUID();
  }
}
