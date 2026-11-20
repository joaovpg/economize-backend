package com.joaovpg.economize.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransferenciaMigrationTest {
  @Inject AgroalDataSource dataSource;

  @Test
  void migraLadosLegadosSemAlterarCategoriasEProtegeAgregado() throws SQLException {
    var schema = "transferencia_" + UUID.randomUUID().toString().replace("-", "");
    var ids = new DadosLegados();

    try {
      flyway(schema, "4").migrate();
      inserirDadosLegados(schema, ids);
      flyway(schema, null).migrate();

      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        try {
          statement.execute("SET search_path TO \"" + schema + "\"");
          assertEquals(
              "Transferências",
              valor(
                  statement,
                  "SELECT STR_NOME FROM TB003_CATEGORIA WHERE ID_REGISTRO = '"
                      + ids.categoriaConflitante
                      + "'"));
          assertEquals(
              "Transferências (pessoal)",
              valor(
                  statement,
                  "SELECT STR_NOME FROM TB003_CATEGORIA WHERE ID_REGISTRO = '"
                      + ids.categoriaPessoal
                      + "'"));
          assertFalse(colunas(statement, schema, "tb003_categoria").contains("STR_TIPO"));

          assertEquals(
              "EFETIVADA",
              valor(
                  statement,
                  "SELECT STR_SITUACAO FROM TB007_TRANSFERENCIA WHERE ID_REGISTRO = '"
                      + ids.transferencia
                      + "'"));
          assertEquals(
              "DESPESA",
              valor(
                  statement,
                  "SELECT STR_TIPO FROM TB006_TRANSACAO WHERE ID_REGISTRO = '" + ids.saida + "'"));
          assertEquals(
              "RECEITA",
              valor(
                  statement,
                  "SELECT STR_TIPO FROM TB006_TRANSACAO WHERE ID_REGISTRO = '"
                      + ids.entrada
                      + "'"));
          assertNull(
              valor(
                  statement,
                  "SELECT ID_CATEGORIA FROM TB006_TRANSACAO WHERE ID_REGISTRO = '"
                      + ids.saida
                      + "'"));
          assertEquals(
              "Transferencia canonica",
              valor(
                  statement,
                  "SELECT STR_DESCRICAO FROM TB006_TRANSACAO WHERE ID_REGISTRO = '"
                      + ids.saida
                      + "'"));

          var colunas = colunas(statement, schema, "tb007_transferencia");
          assertTrue(colunas.contains("STR_SITUACAO"));
          assertTrue(colunas.contains("DAT_FINANCEIRA"));
          assertFalse(colunas.contains("STR_STATUS"));
          assertFalse(colunas.contains("DAT_TRANSFERENCIA"));
          assertFalse(colunas.contains("ID_TRANSFERENCIA_ESTORNADA"));

          assertThrows(
              SQLException.class,
              () ->
                  statement.execute(
                      """
                      UPDATE TB006_TRANSACAO
                      SET ID_CATEGORIA = '%s'
                      WHERE ID_REGISTRO = '%s'
                      """
                          .formatted(ids.categoriaConflitante, ids.saida)));
          assertThrows(
              SQLException.class,
              () ->
                  statement.execute(
                      "UPDATE TB006_TRANSACAO SET STR_DESCRICAO = 'Isolada' WHERE ID_REGISTRO = '"
                          + ids.saida
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

  @Test
  void abortaMigracaoQuandoExisteTransferenciaOrfa() throws SQLException {
    var schema = "transferencia_orfa_" + UUID.randomUUID().toString().replace("-", "");
    var ids = new DadosLegados();

    try {
      flyway(schema, "4").migrate();
      inserirTransferenciaOrfa(schema, ids);

      assertThrows(FlywayException.class, () -> flyway(schema, null).migrate());
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
            ) VALUES ('%s', 'Usuario', 'transferencia-migration@example.com', 'hash',
                      'America/Sao_Paulo', 'ATIVO')
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
        statement.execute(
            """
            INSERT INTO TB003_CATEGORIA (
                ID_REGISTRO, ID_USUARIO, STR_NOME, STR_COR, BOL_ATIVO
            ) VALUES
                ('%s', '%s', 'Transferências', '#112233', TRUE),
                ('%s', '%s', 'Transferências (pessoal)', NULL, TRUE)
            """
                .formatted(
                    ids.categoriaConflitante, ids.usuario, ids.categoriaPessoal, ids.usuario));
        statement.execute(
            """
            INSERT INTO TB006_TRANSACAO (
                ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, ID_CATEGORIA,
                STR_TIPO, STR_SITUACAO, STR_DESCRICAO, DEC_VALOR, DAT_FINANCEIRA,
                DHR_EFETIVACAO
            ) VALUES
                ('%s', '%s', '%s', '%s', 'TRANSFERENCIA', 'EFETIVADA', 'Saida antiga',
                 50, DATE '2026-02-01', TIMESTAMPTZ '2026-02-01 12:00:00Z'),
                ('%s', '%s', '%s', '%s', 'TRANSFERENCIA', 'EFETIVADA', 'Entrada antiga',
                 50, DATE '2026-02-01', TIMESTAMPTZ '2026-02-01 12:00:00Z')
            """
                .formatted(
                    ids.saida,
                    ids.usuario,
                    ids.contaOrigem,
                    ids.categoriaConflitante,
                    ids.entrada,
                    ids.usuario,
                    ids.contaDestino,
                    ids.categoriaConflitante));
        statement.execute(
            """
            INSERT INTO TB007_TRANSFERENCIA (
                ID_REGISTRO, ID_USUARIO, ID_CONTA_ORIGEM, ID_CONTA_DESTINO,
                ID_TRANSACAO_SAIDA, ID_TRANSACAO_ENTRADA, STR_STATUS, STR_DESCRICAO,
                STR_OBSERVACOES, DEC_VALOR, DAT_TRANSFERENCIA, DHR_EFETIVACAO
            ) VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 'ESTORNADA',
                      'Transferencia canonica', 'Migrada', 50, DATE '2026-02-01',
                      TIMESTAMPTZ '2026-02-01 12:00:00Z')
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

  private void inserirTransferenciaOrfa(String schema, DadosLegados ids) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try {
        statement.execute("SET search_path TO \"" + schema + "\"");
        statement.execute(
            """
            INSERT INTO TB001_USUARIO (
                ID_REGISTRO, STR_NOME, STR_EMAIL, STR_SENHA_HASH, STR_TIMEZONE, STR_STATUS
            ) VALUES ('%s', 'Usuario', 'transferencia-orfa@example.com', 'hash',
                      'America/Sao_Paulo', 'ATIVO')
            """
                .formatted(ids.usuario));
        statement.execute(
            """
            INSERT INTO TB002_CONTA_FINANCEIRA (
                ID_REGISTRO, ID_USUARIO, STR_NOME, STR_MOEDA, DEC_SALDO_INICIAL,
                DAT_SALDO_INICIAL, BOL_ATIVO
            ) VALUES ('%s', '%s', 'Origem', 'BRL', 0, DATE '2026-01-01', TRUE)
            """
                .formatted(ids.contaOrigem, ids.usuario));
        statement.execute(
            """
            INSERT INTO TB006_TRANSACAO (
                ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, STR_TIPO, STR_SITUACAO,
                STR_DESCRICAO, DEC_VALOR, DAT_FINANCEIRA
            ) VALUES ('%s', '%s', '%s', 'TRANSFERENCIA', 'PLANEJADA', 'Orfa',
                      10, DATE '2026-02-01')
            """
                .formatted(ids.orfa, ids.usuario, ids.contaOrigem));
      } finally {
        statement.execute("SET search_path TO public");
      }
    }
  }

  private java.util.Set<String> colunas(java.sql.Statement statement, String schema, String tabela)
      throws SQLException {
    var nomes = new java.util.HashSet<String>();
    try (var resultado =
        statement.executeQuery(
            """
            SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'
            """
                .formatted(schema, tabela))) {
      while (resultado.next()) {
        nomes.add(resultado.getString(1).toUpperCase());
      }
    }
    return nomes;
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
    private final UUID categoriaConflitante = UUID.randomUUID();
    private final UUID categoriaPessoal = UUID.randomUUID();
    private final UUID saida = UUID.randomUUID();
    private final UUID entrada = UUID.randomUUID();
    private final UUID orfa = UUID.randomUUID();
    private final UUID transferencia = UUID.randomUUID();
  }
}
