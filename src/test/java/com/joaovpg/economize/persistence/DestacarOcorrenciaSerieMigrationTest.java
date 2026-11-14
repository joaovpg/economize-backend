package com.joaovpg.economize.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DestacarOcorrenciaSerieMigrationTest {
  @Inject AgroalDataSource dataSource;

  @Test
  void preservaTransacoesExistentesEAceitaSomenteOrigemDeSimplesDesligadaUmaVez()
      throws SQLException {
    var schema = "destacar_" + UUID.randomUUID().toString().replace("-", "");
    var usuario = UUID.randomUUID();
    var conta = UUID.randomUUID();
    var grupo = UUID.randomUUID();
    var segmento = UUID.randomUUID();
    var simples = UUID.randomUUID();
    try {
      flyway(schema, "8").migrate();
      executar(
          schema,
          """
          INSERT INTO TB001_USUARIO (ID_REGISTRO, STR_NOME, STR_EMAIL, STR_SENHA_HASH,
              STR_TIMEZONE, BOL_ATIVO)
          VALUES ('%1$s', 'Usuario', 'destacar-migration@example.com', 'hash',
              'America/Sao_Paulo', TRUE);
          INSERT INTO TB002_CONTA_FINANCEIRA (ID_REGISTRO, ID_USUARIO, STR_NOME, STR_MOEDA,
              DEC_SALDO_INICIAL, DAT_SALDO_INICIAL, BOL_ATIVO)
          VALUES ('%2$s', '%1$s', 'Conta', 'BRL', 0, DATE '2026-01-01', TRUE);
          INSERT INTO TB004_GRUPO_RECORRENCIA (ID_REGISTRO, ID_USUARIO, STR_DESCRICAO,
              STR_STATUS, STR_TIPO)
          VALUES ('%3$s', '%1$s', 'Aluguel', 'ATIVO', 'RECORRENCIA');
          INSERT INTO TB005_SEGMENTO_RECORRENCIA (ID_REGISTRO, ID_USUARIO, ID_GRUPO_RECORRENCIA,
              ID_CONTA_FINANCEIRA, STR_TIPO_TRANSACAO, STR_DESCRICAO, DEC_VALOR, DAT_DTSTART,
              STR_RRULE, STR_STATUS, STR_POLITICA_DATA)
          VALUES ('%4$s', '%1$s', '%3$s', '%2$s', 'DESPESA', 'Aluguel', 50,
              DATE '2026-01-10', 'FREQ=MONTHLY;BYMONTHDAY=10', 'ATIVO', 'PADRAO');
          INSERT INTO TB006_TRANSACAO (ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA,
              STR_TIPO, STR_SITUACAO, STR_DESCRICAO, DEC_VALOR, DAT_FINANCEIRA)
          VALUES ('%5$s', '%1$s', '%2$s', 'DESPESA', 'PLANEJADA', 'Mercado', 10,
              DATE '2026-03-05');
          """
              .formatted(usuario, conta, grupo, segmento, simples));

      flyway(schema, null).migrate();

      assertEquals(List.of("1"), consultar(schema, "SELECT COUNT(*) FROM TB006_TRANSACAO"));
      assertEquals(
          List.of("0"),
          consultar(
              schema, "SELECT COUNT(*) FROM TB006_TRANSACAO WHERE ID_SEGMENTO_ORIGEM IS NOT NULL"));

      executar(schema, inserirOrigem(usuario, conta, segmento, "2026-03-10", "2"));
      assertEquals(
          List.of("1"),
          consultar(
              schema, "SELECT COUNT(*) FROM TB006_TRANSACAO WHERE ID_SEGMENTO_ORIGEM IS NOT NULL"));

      assertThrows(
          SQLException.class,
          () -> executar(schema, inserirOrigem(usuario, conta, segmento, "2026-03-10", "NULL")),
          "a mesma ocorrencia so pode ser desligada uma vez");
      assertThrows(
          SQLException.class,
          () -> executar(schema, inserirOrigem(usuario, conta, segmento, "2026-04-10", "0")),
          "numero de parcela deve ser positivo");
      assertThrows(
          SQLException.class,
          () -> executar(schema, linhaSemData(usuario, conta, segmento)),
          "origem exige data original");
      assertThrows(
          SQLException.class,
          () -> executar(schema, origemComGrupo(usuario, conta, grupo, segmento)),
          "origem nao combina com grupo de recorrencia");
      assertThrows(
          SQLException.class,
          () -> executar(schema, origemComExcecao(usuario, conta, grupo, segmento)),
          "origem nao combina com excecao de recorrencia");
    } finally {
      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        statement.execute("SET search_path TO public");
      }
    }
  }

  private static String inserirOrigem(
      UUID usuario, UUID conta, UUID segmento, String data, String numero) {
    return """
    INSERT INTO TB006_TRANSACAO (ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, STR_TIPO,
        STR_SITUACAO, STR_DESCRICAO, DEC_VALOR, DAT_FINANCEIRA, ID_SEGMENTO_ORIGEM,
        DAT_ORIGEM_RECORRENCIA, INT_NUMERO_PARCELA_ORIGEM)
    VALUES ('%s', '%s', '%s', 'DESPESA', 'PLANEJADA', 'Origem', 10, DATE '%s',
        '%s', DATE '%s', %s);
    """
        .formatted(UUID.randomUUID(), usuario, conta, data, segmento, data, numero);
  }

  private static String linhaSemData(UUID usuario, UUID conta, UUID segmento) {
    return """
    INSERT INTO TB006_TRANSACAO (ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA, STR_TIPO,
        STR_SITUACAO, STR_DESCRICAO, DEC_VALOR, DAT_FINANCEIRA, ID_SEGMENTO_ORIGEM)
    VALUES ('%s', '%s', '%s', 'DESPESA', 'PLANEJADA', 'Sem data', 10, DATE '2026-07-01', '%s');
    """
        .formatted(UUID.randomUUID(), usuario, conta, segmento);
  }

  private static String origemComGrupo(UUID usuario, UUID conta, UUID grupo, UUID segmento) {
    return """
    INSERT INTO TB006_TRANSACAO (ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA,
        ID_GRUPO_RECORRENCIA, ID_SEGMENTO_RECORRENCIA, DAT_IDENTIFICADOR_RECORRENCIA,
        STR_TIPO, STR_SITUACAO, STR_DESCRICAO, DEC_VALOR, DAT_FINANCEIRA,
        ID_SEGMENTO_ORIGEM, DAT_ORIGEM_RECORRENCIA)
    VALUES ('%s', '%s', '%s', '%s', '%s', DATE '2026-08-01', 'DESPESA', 'PLANEJADA',
        'Com grupo', 10, DATE '2026-08-01', '%s', DATE '2026-08-01');
    """
        .formatted(UUID.randomUUID(), usuario, conta, grupo, segmento, segmento);
  }

  private static String origemComExcecao(UUID usuario, UUID conta, UUID grupo, UUID segmento) {
    return """
    INSERT INTO TB006_TRANSACAO (ID_REGISTRO, ID_USUARIO, ID_CONTA_FINANCEIRA,
        ID_GRUPO_RECORRENCIA, ID_SEGMENTO_RECORRENCIA, DAT_IDENTIFICADOR_RECORRENCIA,
        BOL_EXCECAO_RECORRENCIA, STR_TIPO, STR_SITUACAO, STR_DESCRICAO, DEC_VALOR,
        DAT_FINANCEIRA, ID_SEGMENTO_ORIGEM, DAT_ORIGEM_RECORRENCIA)
    VALUES ('%s', '%s', '%s', '%s', '%s', DATE '2026-09-10', TRUE, 'DESPESA', 'PLANEJADA',
        'Excecao', 10, DATE '2026-09-10', '%s', DATE '2026-09-10');
    """
        .formatted(UUID.randomUUID(), usuario, conta, grupo, segmento, segmento);
  }

  private List<String> consultar(String schema, String sql) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      try {
        statement.execute("SET search_path TO \"" + schema + "\"");
        try (var resultado = statement.executeQuery(sql)) {
          var linhas = new ArrayList<String>();
          while (resultado.next()) {
            linhas.add(String.valueOf(resultado.getObject(1)));
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
}
