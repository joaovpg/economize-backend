package com.joaovpg.economize.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContaFinanceiraMigrationTest {
  @Inject AgroalDataSource dataSource;

  @Test
  void migraHistoricoExistenteParaDadosIniciaisBloqueados() throws SQLException {
    var schema = "migracao_" + UUID.randomUUID().toString().replace("-", "");
    var usuarioId = UUID.randomUUID();
    var contaId = UUID.randomUUID();
    var transacaoId = UUID.randomUUID();

    try {
      flyway(schema, "1").migrate();
      inserirHistoricoLegado(schema, usuarioId, contaId, transacaoId);
      flyway(schema, null).migrate();

      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        try {
          statement.execute("SET search_path TO \"" + schema + "\"");
          try (var resultado =
              statement.executeQuery(
                  """
                  SELECT BOL_DADOS_INICIAIS_BLOQUEADOS
                  FROM TB002_CONTA_FINANCEIRA
                  WHERE ID_REGISTRO = '%s'
                  """
                      .formatted(contaId))) {
            assertTrue(resultado.next());
            assertTrue(resultado.getBoolean(1));
          }
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
  void migraStatusUsuarioParaFlagBooleano() throws SQLException {
    var schema = "usuario_" + UUID.randomUUID().toString().replace("-", "");
    var usuarioAtivoId = UUID.randomUUID();
    var usuarioBloqueadoId = UUID.randomUUID();

    try {
      flyway(schema, "1").migrate();
      inserirUsuariosLegados(schema, usuarioAtivoId, usuarioBloqueadoId);
      flyway(schema, null).migrate();

      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        try {
          statement.execute("SET search_path TO \"" + schema + "\"");
          try (var coluna =
              statement.executeQuery(
                  """
                  SELECT DATA_TYPE
                  FROM INFORMATION_SCHEMA.COLUMNS
                  WHERE TABLE_SCHEMA = '%s'
                    AND TABLE_NAME = 'tb001_usuario'
                    AND COLUMN_NAME = 'bol_ativo'
                  """
                      .formatted(schema))) {
            assertTrue(coluna.next());
            assertEquals("boolean", coluna.getString(1));
          }
          assertFalse(existeColuna(statement, schema, "tb001_usuario", "str_status"));
          assertTrue(
              booleano(
                  statement,
                  "SELECT BOL_ATIVO FROM TB001_USUARIO WHERE ID_REGISTRO = '"
                      + usuarioAtivoId
                      + "'"));
          assertFalse(
              booleano(
                  statement,
                  "SELECT BOL_ATIVO FROM TB001_USUARIO WHERE ID_REGISTRO = '"
                      + usuarioBloqueadoId
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

  private void inserirHistoricoLegado(String schema, UUID usuarioId, UUID contaId, UUID transacaoId)
      throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try {
        statement.execute("SET search_path TO \"" + schema + "\"");
        statement.execute(
            """
            INSERT INTO TB001_USUARIO (
                ID_REGISTRO, STR_NOME, STR_EMAIL, STR_SENHA_HASH, STR_TIMEZONE, STR_STATUS
            ) VALUES ('%s', 'Usuario', 'legado@example.com', 'hash', 'America/Sao_Paulo', 'ATIVO')
            """
                .formatted(usuarioId));
        statement.execute(
            """
            INSERT INTO TB002_CONTA_FINANCEIRA (
                ID_REGISTRO, ID_USUARIO, STR_NOME, STR_MOEDA, DEC_SALDO_INICIAL,
                DAT_SALDO_INICIAL, BOL_ATIVO
            ) VALUES ('%s', '%s', 'Legado', 'BRL', 0, DATE '2026-01-01', TRUE)
            """
                .formatted(contaId, usuarioId));
        statement.execute(
            """
            INSERT INTO TB006_TRANSACAO (
                ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, STR_TIPO, STR_STATUS,
                STR_DESCRICAO, DEC_VALOR, DAT_VENCIMENTO
            ) VALUES (
                '%s', '%s', '%s', 'DESPESA', 'PLANEJADA', 'Historico', 10, DATE '2026-01-01'
            )
            """
                .formatted(transacaoId, usuarioId, contaId));
      } finally {
        statement.execute("SET search_path TO public");
      }
    }
  }

  private void inserirUsuariosLegados(String schema, UUID usuarioAtivoId, UUID usuarioBloqueadoId)
      throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try {
        statement.execute("SET search_path TO \"" + schema + "\"");
        statement.execute(
            """
            INSERT INTO TB001_USUARIO (
                ID_REGISTRO, STR_NOME, STR_EMAIL, STR_SENHA_HASH, STR_TIMEZONE, STR_STATUS
            ) VALUES
                ('%s', 'Ativo', 'ativo-%s@example.com', 'hash', 'America/Sao_Paulo', 'ATIVO'),
                ('%s', 'Bloqueado', 'bloqueado-%s@example.com', 'hash', 'America/Sao_Paulo', 'BLOQUEADO')
            """
                .formatted(usuarioAtivoId, usuarioAtivoId, usuarioBloqueadoId, usuarioBloqueadoId));
      } finally {
        statement.execute("SET search_path TO public");
      }
    }
  }

  private boolean existeColuna(
      java.sql.Statement statement, String schema, String tabela, String coluna)
      throws SQLException {
    try (var resultado =
        statement.executeQuery(
            """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s' AND COLUMN_NAME = '%s'
            """
                .formatted(schema, tabela, coluna))) {
      resultado.next();
      return resultado.getInt(1) > 0;
    }
  }

  private boolean booleano(java.sql.Statement statement, String sql) throws SQLException {
    try (var resultado = statement.executeQuery(sql)) {
      resultado.next();
      return resultado.getBoolean(1);
    }
  }
}
