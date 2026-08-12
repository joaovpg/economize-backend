package com.joaovpg.economize.transferencia.application;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.transferencia.SituacaoTransferencia;
import com.joaovpg.economize.transferencia.Transferencia;
import com.joaovpg.economize.transferencia.TransferenciaRepository;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class CriarTransferencia {
  private final UsuarioRepository usuarioRepository;
  private final ContaFinanceiraRepository contaRepository;
  private final TransacaoRepository transacaoRepository;
  private final TransferenciaRepository transferenciaRepository;

  public CriarTransferencia(
      UsuarioRepository usuarioRepository,
      ContaFinanceiraRepository contaRepository,
      TransacaoRepository transacaoRepository,
      TransferenciaRepository transferenciaRepository) {
    this.usuarioRepository = usuarioRepository;
    this.contaRepository = contaRepository;
    this.transacaoRepository = transacaoRepository;
    this.transferenciaRepository = transferenciaRepository;
  }

  @Transactional
  public TransferenciaResultado executar(Comando comando) {
    validar(comando);
    var usuario =
        usuarioRepository.buscarAtivo(comando.usuarioId()).orElseThrow(this::naoEncontrada);
    var origem =
        contaRepository
            .buscarAtivaDoUsuario(comando.contaOrigemId(), comando.usuarioId())
            .orElseThrow(this::naoEncontrada);
    var destino =
        contaRepository
            .buscarAtivaDoUsuario(comando.contaDestinoId(), comando.usuarioId())
            .orElseThrow(this::naoEncontrada);
    validarContas(
        comando,
        origem.getMoeda(),
        destino.getMoeda(),
        origem.getDataSaldoInicial(),
        destino.getDataSaldoInicial());
    if (comando.situacao() == SituacaoTransferencia.EFETIVADA
        && comando.dataFinanceira().isAfter(LocalDate.now(ZoneId.of(usuario.getTimezone())))) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_FUTURA",
          "Uma Transferencia efetivada nao pode ter data financeira futura");
    }
    var efetivadoEm =
        comando.situacao() == SituacaoTransferencia.EFETIVADA
            ? Instant.now().truncatedTo(ChronoUnit.MICROS)
            : null;

    var saida = novaTransacao(TipoTransacao.DESPESA, origem, comando, efetivadoEm, usuario);
    var entrada = novaTransacao(TipoTransacao.RECEITA, destino, comando, efetivadoEm, usuario);
    transacaoRepository.persist(saida);
    transacaoRepository.persist(entrada);

    var transferencia = new Transferencia();
    transferencia.setUsuario(usuario);
    transferencia.setContaOrigem(origem);
    transferencia.setContaDestino(destino);
    transferencia.setTransacaoSaida(saida);
    transferencia.setTransacaoEntrada(entrada);
    transferencia.setSituacao(comando.situacao());
    transferencia.setDescricao(comando.descricao().strip());
    transferencia.setObservacoes(normalizar(comando.observacoes()));
    transferencia.setValor(comando.valor());
    transferencia.setDataFinanceira(comando.dataFinanceira());
    transferencia.setEfetivadoEm(efetivadoEm);
    transferenciaRepository.persist(transferencia);

    return resultado(transferencia);
  }

  private Transacao novaTransacao(
      TipoTransacao tipo,
      ContaFinanceira conta,
      Comando comando,
      Instant efetivadoEm,
      Usuario usuario) {
    var transacao = new Transacao();
    transacao.setUsuario(usuario);
    transacao.setConta(conta);
    transacao.setTipo(tipo);
    transacao.setSituacao(SituacaoTransacao.valueOf(comando.situacao().name()));
    transacao.setDescricao(comando.descricao().strip());
    transacao.setObservacoes(normalizar(comando.observacoes()));
    transacao.setValor(comando.valor());
    transacao.setDataFinanceira(comando.dataFinanceira());
    transacao.setEfetivadoEm(efetivadoEm);
    return transacao;
  }

  private void validar(Comando comando) {
    if (comando.contaOrigemId() == null || comando.contaDestinoId() == null) {
      throw new RegraNegocioException(
          "CONTAS_OBRIGATORIAS", "Contas de origem e destino sao obrigatorias");
    }
    if (comando.contaOrigemId().equals(comando.contaDestinoId())) {
      throw new RegraNegocioException(
          "CONTAS_TRANSFERENCIA_IGUAIS", "Origem e destino devem ser diferentes");
    }
    if (comando.situacao() == null) {
      throw new RegraNegocioException(
          "SITUACAO_TRANSFERENCIA_INVALIDA", "Situacao da Transferencia invalida");
    }
    if (comando.valor() == null
        || comando.valor().signum() <= 0
        || comando.valor().scale() > 4
        || comando.valor().setScale(4).precision() > 19) {
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

  private void validarContas(
      Comando comando,
      String moedaOrigem,
      String moedaDestino,
      LocalDate dataInicialOrigem,
      LocalDate dataInicialDestino) {
    if (!moedaOrigem.equals(moedaDestino)) {
      throw new RegraNegocioException(
          "MOEDAS_TRANSFERENCIA_DIFERENTES", "Contas da Transferencia devem usar a mesma moeda");
    }
    if (comando.dataFinanceira().isBefore(dataInicialOrigem)
        || comando.dataFinanceira().isBefore(dataInicialDestino)) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL",
          "A data financeira nao pode ser anterior a data do saldo inicial das contas");
    }
  }

  private String normalizar(String valor) {
    return valor == null ? null : valor.strip();
  }

  private TransferenciaResultado resultado(Transferencia transferencia) {
    return new TransferenciaResultado(
        transferencia.getId(),
        transferencia.getContaOrigem().getId(),
        transferencia.getContaDestino().getId(),
        transferencia.getSituacao(),
        transferencia.getDescricao(),
        transferencia.getObservacoes(),
        transferencia.getValor(),
        transferencia.getDataFinanceira(),
        transferencia.getEfetivadoEm());
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
