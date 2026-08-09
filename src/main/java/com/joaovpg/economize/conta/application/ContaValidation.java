package com.joaovpg.economize.conta.application;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

final class ContaValidation {
  private ContaValidation() {}

  static String nome(String nome) {
    if (nome == null || nome.isBlank() || nome.strip().length() > 120) {
      throw new RegraNegocioException("NOME_CONTA_INVALIDO", "Nome da conta invalido");
    }
    return nome.strip();
  }

  static String moeda(String moeda) {
    if (!"BRL".equals(moeda)) {
      throw new RegraNegocioException("MOEDA_CONTA_INVALIDA", "Moeda da conta invalida");
    }
    return moeda;
  }

  static BigDecimal saldoInicial(BigDecimal saldoInicial) {
    if (saldoInicial == null
        || saldoInicial.scale() > 4
        || saldoInicial.setScale(4).precision() > 19) {
      throw new RegraNegocioException("SALDO_INICIAL_INVALIDO", "Saldo inicial invalido");
    }
    return saldoInicial;
  }

  static LocalDate dataSaldoInicial(LocalDate dataSaldoInicial, LocalDate dataAtual) {
    if (dataSaldoInicial == null || dataSaldoInicial.isAfter(dataAtual)) {
      throw new RegraNegocioException(
          "DATA_SALDO_INICIAL_INVALIDA", "Data do saldo inicial invalida");
    }
    return dataSaldoInicial;
  }

  static void nomeDisponivel(ContaFinanceiraRepository repository, UUID usuarioId, String nome) {
    if (repository.existeComNome(usuarioId, nome)) {
      throw new RegraNegocioException("NOME_CONTA_DUPLICADO", "Ja existe uma conta com esse nome");
    }
  }

  static void nomeDisponivel(
      ContaFinanceiraRepository repository, UUID usuarioId, String nome, UUID ignorarId) {
    if (repository.existeOutroComNome(usuarioId, nome, ignorarId)) {
      throw new RegraNegocioException("NOME_CONTA_DUPLICADO", "Ja existe uma conta com esse nome");
    }
  }

  static void dadosIniciaisEditaveis(
      ContaFinanceira conta, String moeda, BigDecimal saldoInicial, LocalDate dataSaldoInicial) {
    if (conta.isDadosIniciaisBloqueados()
        && (!conta.getMoeda().equals(moeda)
            || conta.getSaldoInicial().compareTo(saldoInicial) != 0
            || !conta.getDataSaldoInicial().equals(dataSaldoInicial))) {
      throw new RegraNegocioException(
          "DADOS_INICIAIS_CONTA_IMUTAVEIS",
          "Os dados iniciais da conta nao podem mais ser alterados");
    }
  }

  static void flush(ContaFinanceiraRepository repository) {
    try {
      repository.flush();
    } catch (org.hibernate.exception.ConstraintViolationException exception) {
      if ("ix002_02_nome_usuario".equalsIgnoreCase(exception.getConstraintName())) {
        throw new RegraNegocioException(
            "NOME_CONTA_DUPLICADO", "Ja existe uma conta com esse nome");
      }
      throw exception;
    }
  }

  static ContaResultado resultado(ContaFinanceira conta) {
    return new ContaResultado(
        conta.getId(),
        conta.getNome(),
        conta.getMoeda(),
        conta.getSaldoInicial(),
        conta.getDataSaldoInicial(),
        conta.isAtivo());
  }
}
