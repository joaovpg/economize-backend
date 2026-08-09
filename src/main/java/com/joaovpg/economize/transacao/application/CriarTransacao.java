package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.usuario.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@ApplicationScoped
public class CriarTransacao {
  private final UsuarioRepository usuarioRepository;
  private final ContaFinanceiraRepository contaRepository;
  private final CategoriaRepository categoriaRepository;
  private final TransacaoRepository transacaoRepository;

  public CriarTransacao(
      UsuarioRepository usuarioRepository,
      ContaFinanceiraRepository contaRepository,
      CategoriaRepository categoriaRepository,
      TransacaoRepository transacaoRepository) {
    this.usuarioRepository = usuarioRepository;
    this.contaRepository = contaRepository;
    this.categoriaRepository = categoriaRepository;
    this.transacaoRepository = transacaoRepository;
  }

  @Transactional
  public Resultado executar(Comando comando) {
    validar(comando);
    var usuario =
        usuarioRepository
            .buscarAtivo(comando.usuarioId())
            .orElseThrow(() -> recursoNaoEncontrado("Usuario"));
    if (comando.situacao() == SituacaoTransacao.EFETIVADA
        && comando.dataFinanceira().isAfter(LocalDate.now(ZoneId.of(usuario.getTimezone())))) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_FUTURA", "Uma transacao efetivada nao pode ter data financeira futura");
    }
    var conta =
        contaRepository
            .buscarAtivaDoUsuario(comando.contaId(), comando.usuarioId())
            .orElseThrow(() -> recursoNaoEncontrado("Conta"));
    if (comando.dataFinanceira().isBefore(conta.getDataSaldoInicial())) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL",
          "A data financeira nao pode ser anterior a data do saldo inicial da conta");
    }
    Categoria categoria =
        comando.categoriaId() == null
            ? null
            : categoriaRepository
                .buscarAtivaDoUsuario(comando.categoriaId(), comando.usuarioId())
                .orElseThrow(() -> recursoNaoEncontrado("Categoria"));
    var transacao = new Transacao();
    transacao.setUsuario(usuario);
    transacao.setConta(conta);
    transacao.setCategoria(categoria);
    transacao.setTipo(comando.tipo());
    transacao.setSituacao(comando.situacao());
    transacao.setDescricao(comando.descricao().strip());
    transacao.setObservacoes(comando.observacoes() == null ? null : comando.observacoes().strip());
    transacao.setValor(comando.valor());
    transacao.setDataFinanceira(comando.dataFinanceira());
    transacao.setEfetivadoEm(
        comando.situacao() == SituacaoTransacao.EFETIVADA ? Instant.now() : null);
    transacaoRepository.persist(transacao);
    return new Resultado(
        transacao.getId(),
        transacao.getTipo(),
        transacao.getSituacao(),
        transacao.getDescricao(),
        transacao.getObservacoes(),
        transacao.getValor(),
        transacao.getDataFinanceira(),
        transacao.getEfetivadoEm(),
        conta.getId(),
        categoria == null ? null : categoria.getId());
  }

  private void validar(Comando comando) {
    if (comando.situacao() == null) {
      throw new RegraNegocioException(
          "SITUACAO_TRANSACAO_INVALIDA", "Situacao da transacao invalida");
    }
    if (comando.tipo() == null) {
      throw new RegraNegocioException("TIPO_TRANSACAO_INVALIDO", "Tipo da transacao invalido");
    }
    if (comando.valor() == null
        || comando.valor().signum() <= 0
        || comando.valor().scale() > 4
        || comando.valor().setScale(4).precision() > 19) {
      throw new RegraNegocioException("VALOR_TRANSACAO_INVALIDO", "Valor da transacao invalido");
    }
    if (comando.descricao() == null
        || comando.descricao().isBlank()
        || comando.descricao().strip().length() > 255) {
      throw new RegraNegocioException(
          "DESCRICAO_TRANSACAO_INVALIDA", "Descricao da transacao invalida");
    }
    if (comando.observacoes() != null && comando.observacoes().length() > 2000) {
      throw new RegraNegocioException(
          "OBSERVACOES_TRANSACAO_INVALIDAS", "Observacoes da transacao invalidas");
    }
    if (comando.dataFinanceira() == null) {
      throw new RegraNegocioException("DATA_FINANCEIRA_OBRIGATORIA", "Data financeira obrigatoria");
    }
  }

  private RecursoNaoEncontradoException recursoNaoEncontrado(String recurso) {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrado");
  }

  public record Comando(
      UUID usuarioId,
      UUID contaId,
      UUID categoriaId,
      SituacaoTransacao situacao,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira) {}

  public record Resultado(
      UUID id,
      TipoTransacao tipo,
      SituacaoTransacao situacao,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      java.time.Instant efetivadoEm,
      UUID contaId,
      UUID categoriaId) {}
}
