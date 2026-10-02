package com.joaovpg.economize.transferencia.application;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transferencia.SituacaoTransferencia;
import com.joaovpg.economize.transferencia.TransferenciaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class AlterarTransferencia {
  private final TransferenciaRepository transferenciaRepository;
  private final ContaFinanceiraRepository contaRepository;

  public AlterarTransferencia(
      TransferenciaRepository transferenciaRepository, ContaFinanceiraRepository contaRepository) {
    this.transferenciaRepository = transferenciaRepository;
    this.contaRepository = contaRepository;
  }

  @Transactional
  public TransferenciaResultado executar(UUID transferenciaId, Comando comando) {
    validar(comando);
    var transferencia =
        transferenciaRepository
            .buscarDoUsuarioParaEdicao(transferenciaId, comando.usuarioId())
            .orElseThrow(this::naoEncontrada);
    var origem =
        resolverConta(transferencia.getContaOrigem(), comando.contaOrigemId(), comando.usuarioId());
    var destino =
        resolverConta(
            transferencia.getContaDestino(), comando.contaDestinoId(), comando.usuarioId());
    if (origem.getId().equals(destino.getId())) {
      throw new RegraNegocioException(
          "CONTAS_TRANSFERENCIA_IGUAIS", "Origem e destino devem ser diferentes");
    }
    if (!origem.getMoeda().equals(destino.getMoeda())) {
      throw new RegraNegocioException(
          "MOEDAS_TRANSFERENCIA_DIFERENTES", "Contas da Transferencia devem usar a mesma moeda");
    }
    if (comando.dataFinanceira().isBefore(origem.getDataSaldoInicial())
        || comando.dataFinanceira().isBefore(destino.getDataSaldoInicial())) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL",
          "A data financeira nao pode ser anterior a data do saldo inicial das contas");
    }
    if (comando.situacao() == SituacaoTransferencia.EFETIVADA
        && comando
            .dataFinanceira()
            .isAfter(LocalDate.now(ZoneId.of(transferencia.getUsuario().getTimezone())))) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_FUTURA",
          "Uma Transferencia efetivada nao pode ter data financeira futura");
    }

    var situacaoAnterior = transferencia.getSituacao();
    Instant efetivadoEm = transferencia.getEfetivadoEm();
    if (comando.situacao() == SituacaoTransferencia.PLANEJADA) {
      efetivadoEm = null;
    } else if (situacaoAnterior == SituacaoTransferencia.PLANEJADA) {
      efetivadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    transferencia.setContaOrigem(origem);
    transferencia.setContaDestino(destino);
    transferencia.setSituacao(comando.situacao());
    transferencia.setDescricao(comando.descricao().strip());
    transferencia.setObservacoes(normalizar(comando.observacoes()));
    transferencia.setValor(comando.valor());
    transferencia.setDataFinanceira(comando.dataFinanceira());
    transferencia.setEfetivadoEm(efetivadoEm);
    atualizarLado(
        transferencia.getTransacaoSaida(), origem, TipoTransacao.DESPESA, comando, efetivadoEm);
    atualizarLado(
        transferencia.getTransacaoEntrada(), destino, TipoTransacao.RECEITA, comando, efetivadoEm);

    return new TransferenciaResultado(
        transferencia.getId(),
        origem.getId(),
        destino.getId(),
        transferencia.getSituacao(),
        transferencia.getDescricao(),
        transferencia.getObservacoes(),
        transferencia.getValor(),
        transferencia.getDataFinanceira(),
        transferencia.getEfetivadoEm());
  }

  private ContaFinanceira resolverConta(ContaFinanceira atual, UUID novaId, UUID usuarioId) {
    if (atual.getId().equals(novaId)) {
      return atual;
    }
    return contaRepository.buscarAtivaDoUsuario(novaId, usuarioId).orElseThrow(this::naoEncontrada);
  }

  private void atualizarLado(
      Transacao lado,
      ContaFinanceira conta,
      TipoTransacao tipo,
      Comando comando,
      Instant efetivadoEm) {
    lado.setConta(conta);
    lado.setTipo(tipo);
    lado.setSituacao(SituacaoTransacao.valueOf(comando.situacao().name()));
    lado.setDescricao(comando.descricao().strip());
    lado.setObservacoes(normalizar(comando.observacoes()));
    lado.setValor(comando.valor());
    lado.setDataFinanceira(comando.dataFinanceira());
    lado.setEfetivadoEm(efetivadoEm);
  }

  private void validar(Comando comando) {
    if (comando.contaOrigemId() == null || comando.contaDestinoId() == null) {
      throw new RegraNegocioException(
          "CONTAS_OBRIGATORIAS", "Contas de origem e destino sao obrigatorias");
    }
    if (comando.situacao() == null) {
      throw new RegraNegocioException(
          "SITUACAO_TRANSFERENCIA_INVALIDA", "Situacao da Transferencia invalida");
    }
    if (comando.valor() == null
        || comando.valor().signum() <= 0
        || comando.valor().scale() > 4
        || comando.valor().setScale(4, RoundingMode.UNNECESSARY).precision() > 19) {
      throw new RegraNegocioException(
          "VALOR_TRANSFERENCIA_INVALIDO", "Valor da Transferencia invalido");
    }
    if (comando.descricao() == null
        || comando.descricao().isBlank()
        || comando.descricao().strip().length() > 255) {
      throw new RegraNegocioException(
          "DESCRICAO_TRANSFERENCIA_INVALIDA", "Descricao da Transferencia invalida");
    }
    if (comando.observacoes() != null && comando.observacoes().length() > 2000) {
      throw new RegraNegocioException(
          "OBSERVACOES_TRANSFERENCIA_INVALIDAS", "Observacoes da Transferencia invalidas");
    }
    if (comando.dataFinanceira() == null) {
      throw new RegraNegocioException("DATA_FINANCEIRA_OBRIGATORIA", "Data financeira obrigatoria");
    }
  }

  private String normalizar(String valor) {
    return valor == null ? null : valor.strip();
  }

  private RecursoNaoEncontradoException naoEncontrada() {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Recurso nao encontrado");
  }

  public record Comando(
      UUID usuarioId,
      UUID contaOrigemId,
      UUID contaDestinoId,
      SituacaoTransferencia situacao,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira) {}
}
