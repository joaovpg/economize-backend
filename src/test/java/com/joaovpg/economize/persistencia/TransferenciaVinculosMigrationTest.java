package com.joaovpg.economize.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransferenciaVinculosMigrationTest {
  @Inject AgroalDataSource dataSource;

  @Test
  void vinculosDosLadosDaTransferenciaSaoVerificadosNoCommit() throws SQLException {
    var schema = "transferencia_vinculos_" + UUID.randomUUID().toString().replace("-", "");

    flyway(schema, "8").migrate();
    flyway(schema, null).migrate();

    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var resultado =
            statement.executeQuery(
                """
                SELECT COUNT(*)
                FROM pg_constraint RESTRICAO
                JOIN pg_namespace ESQUEMA ON ESQUEMA.oid = RESTRICAO.connamespace
                WHERE ESQUEMA.nspname = '%s'
                  AND RESTRICAO.conname IN ('fk007_04_transacao_saida', 'fk007_05_transacao_entrada')
                  AND RESTRICAO.condeferrable
                  AND RESTRICAO.condeferred
                """
                    .formatted(schema))) {
      resultado.next();
      assertEquals(2, resultado.getInt(1));
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
}
