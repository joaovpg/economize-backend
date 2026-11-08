package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.usuario.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Regras comuns de edição de transações simples e de ocorrências de série: campos, conta, categoria
 * e datas. Usadas pelas alterações e pela prévia, para que as duas apliquem as mesmas validações.
 */
@ApplicationScoped
public class RegrasEdicaoTransacao {
  private static final int ESCALA_MONETARIA = 4;

  private final ContaFinanceiraRepository contaRepository;
  private final CategoriaRepository categoriaRepository;

  public RegrasEdicaoTransacao(
      ContaFinanceiraRepository contaRepository, CategoriaRepository categoriaRepository) {
    this.contaRepository = contaRepository;
    this.categoriaRepository = categoriaRepository;
  }

  public void validarCamposComuns(
      UUID contaId,
      SituacaoTransacao situacao,
      TipoTransacao tipo,
      BigDecimal valor,
      String descricao,
      String observacoes,
      LocalDate dataFinanceira) {
    if (contaId == null) {
      throw new RegraNegocioException("CONTA_OBRIGATORIA", "Conta obrigatoria");
    }
    if (situacao == null) {
      throw new RegraNegocioException(
          "SITUACAO_TRANSACAO_INVALIDA", "Situacao da transacao invalida");
    }
    if (tipo == null) {
      throw new RegraNegocioException("TIPO_TRANSACAO_INVALIDO", "Tipo da transacao invalido");
    }
    if (valor == null
        || valor.signum() <= 0
        || valor.scale() > ESCALA_MONETARIA
        || valor.setScale(ESCALA_MONETARIA, RoundingMode.UNNECESSARY).precision() > 19) {
      throw new RegraNegocioException("VALOR_TRANSACAO_INVALIDO", "Valor da transacao invalido");
    }
    if (descricao == null || descricao.isBlank() || descricao.strip().length() > 255) {
      throw new RegraNegocioException(
          "DESCRICAO_TRANSACAO_INVALIDA", "Descricao da transacao invalida");
    }
    if (observacoes != null && observacoes.length() > 2000) {
      throw new RegraNegocioException(
          "OBSERVACOES_TRANSACAO_INVALIDAS", "Observacoes da transacao invalidas");
    }
    if (dataFinanceira == null) {
      throw new RegraNegocioException("DATA_FINANCEIRA_OBRIGATORIA", "Data financeira obrigatoria");
    }
  }

  /** Mantém a conta atual quando o id não muda; caso contrário, exige conta ativa do usuário. */
  public ContaFinanceira resolverConta(ContaFinanceira contaAtual, UUID contaId, UUID usuarioId) {
    if (contaAtual != null && contaId.equals(contaAtual.getId())) {
      return contaAtual;
    }
    return contaRepository
        .buscarAtivaDoUsuario(contaId, usuarioId)
        .orElseThrow(() -> naoEncontrado("Conta"));
  }

  public void validarMesmaMoeda(ContaFinanceira contaAtual, ContaFinanceira novaConta) {
    if (!novaConta.getMoeda().equals(contaAtual.getMoeda())) {
      throw new RegraNegocioException(
          "MOEDA_CONTA_INCOMPATIVEL", "A nova conta deve usar a mesma moeda da conta atual");
    }
  }

  public Categoria resolverCategoria(Categoria categoriaAtual, UUID categoriaId, UUID usuarioId) {
    if (categoriaId == null) {
      return null;
    }
    if (categoriaAtual != null && categoriaId.equals(categoriaAtual.getId())) {
      return categoriaAtual;
    }
    return categoriaRepository
        .buscarAtivaDoUsuario(categoriaId, usuarioId)
        .orElseThrow(() -> naoEncontrado("Categoria"));
  }

  public void validarDatas(
      ContaFinanceira conta,
      Usuario usuario,
      LocalDate dataFinanceira,
      SituacaoTransacao situacao) {
    if (dataFinanceira.isBefore(conta.getDataSaldoInicial())) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL",
          "A data financeira nao pode ser anterior a data do saldo inicial da conta");
    }
    if (situacao == SituacaoTransacao.EFETIVADA
        && dataFinanceira.isAfter(LocalDate.now(ZoneId.of(usuario.getTimezone())))) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_FUTURA", "Uma transacao efetivada nao pode ter data financeira futura");
    }
  }

  private static RecursoNaoEncontradoException naoEncontrado(String recurso) {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrada");
  }
}
