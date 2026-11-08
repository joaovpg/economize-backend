package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.application.CriarSerieTransacao;
import com.joaovpg.economize.recorrencia.application.ResolverOcorrenciasRecorrentes;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.shared.exception.ValidacaoException;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AlterarTransacao {
  private final TransacaoRepository transacaoRepository;
  private final ContaFinanceiraRepository contaRepository;
  private final CategoriaRepository categoriaRepository;
  private final CriarSerieTransacao criarSerie;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;

  public AlterarTransacao(
      TransacaoRepository transacaoRepository,
      ContaFinanceiraRepository contaRepository,
      CategoriaRepository categoriaRepository,
      CriarSerieTransacao criarSerie,
      ResolverOcorrenciasRecorrentes resolverOcorrencias) {
    this.transacaoRepository = transacaoRepository;
    this.contaRepository = contaRepository;
    this.categoriaRepository = categoriaRepository;
    this.criarSerie = criarSerie;
    this.resolverOcorrencias = resolverOcorrencias;
  }

  @Transactional
  public ConsultarTransacoes.Item executar(UUID transacaoId, Comando comando) {
    validar(comando);

    var transacao =
        transacaoRepository
            .buscarDoUsuarioParaEdicao(transacaoId, comando.usuarioId())
            .orElseThrow(() -> recursoNaoEncontrado("Transacao"));

    validarTransacaoSimples(transacao);
    if (!IdentidadeEdicaoTransacao.versao(transacao.getVersao()).equals(comando.versaoEdicao())) {
      throw new RegraNegocioException(
          "VERSAO_DESATUALIZADA", "A transacao foi alterada por outra operacao");
    }

    var conta = resolverConta(transacao, comando);
    var categoria = resolverCategoria(transacao, comando);

    if (comando.dataFinanceira().isBefore(conta.getDataSaldoInicial())) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL",
          "A data financeira nao pode ser anterior a data do saldo inicial da conta");
    }

    if (comando.situacao() == SituacaoTransacao.EFETIVADA
        && comando
            .dataFinanceira()
            .isAfter(LocalDate.now(ZoneId.of(transacao.getUsuario().getTimezone())))) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_FUTURA", "Uma transacao efetivada nao pode ter data financeira futura");
    }

    var situacaoAnterior = transacao.getSituacao();
    if (comando.repeticao() != null) {
      return converterEmSerie(transacao, comando, conta, categoria, situacaoAnterior);
    }

    aplicarDadosComuns(transacao, comando, conta, categoria, situacaoAnterior);
    transacaoRepository.flush();
    return ConsultarTransacoes.itemTransacaoSimples(transacao);
  }

  /**
   * Transforma a transação simples na primeira ocorrência de uma nova série, sem cópia. A regra e a
   * numeração são validadas por CriarSerieTransacao antes de qualquer gravação; a transação
   * existente é apenas vinculada ao segmento criado.
   */
  private ConsultarTransacoes.Item converterEmSerie(
      Transacao transacao,
      Comando comando,
      ContaFinanceira conta,
      Categoria categoria,
      SituacaoTransacao situacaoAnterior) {
    var segmento =
        criarSerie.executar(
            new CriarSerieTransacao.Comando(
                transacao.getUsuario(),
                conta,
                categoria,
                comando.tipo(),
                comando.descricao().strip(),
                observacoes(comando),
                comando.valor(),
                comando.dataFinanceira(),
                comando.repeticao()));

    aplicarDadosComuns(transacao, comando, conta, categoria, situacaoAnterior);
    transacao.setGrupoRecorrencia(segmento.getGrupo());
    transacao.setSegmentoRecorrencia(segmento);
    transacao.setIdentificadorRecorrencia(comando.dataFinanceira());
    transacao.setExcecaoRecorrencia(false);
    transacaoRepository.flush();

    var data = comando.dataFinanceira();
    return resolverOcorrencias
        .resolver(List.of(segmento), List.of(transacao), List.of(), data, data)
        .stream()
        .findFirst()
        .map(ConsultarTransacoes::itemRecorrente)
        .orElseThrow(() -> new IllegalStateException("A primeira ocorrencia nao foi projetada"));
  }

  private void aplicarDadosComuns(
      Transacao transacao,
      Comando comando,
      ContaFinanceira conta,
      Categoria categoria,
      SituacaoTransacao situacaoAnterior) {
    transacao.setConta(conta);
    transacao.setCategoria(categoria);
    transacao.setTipo(comando.tipo());
    transacao.setSituacao(comando.situacao());
    transacao.setDescricao(comando.descricao().strip());
    transacao.setObservacoes(observacoes(comando));
    transacao.setValor(comando.valor());
    transacao.setDataFinanceira(comando.dataFinanceira());

    if (comando.situacao() == SituacaoTransacao.PLANEJADA) {
      transacao.setEfetivadoEm(null);
    } else if (situacaoAnterior == SituacaoTransacao.PLANEJADA) {
      transacao.setEfetivadoEm(Instant.now());
    }
  }

  private static String observacoes(Comando comando) {
    return comando.observacoes() == null ? null : comando.observacoes().strip();
  }

  private ContaFinanceira resolverConta(Transacao transacao, Comando comando) {
    if (transacao.getConta().getId().equals(comando.contaId())) {
      return transacao.getConta();
    }

    var conta =
        contaRepository
            .buscarAtivaDoUsuario(comando.contaId(), comando.usuarioId())
            .orElseThrow(() -> recursoNaoEncontrado("Conta"));

    if (!conta.getMoeda().equals(transacao.getConta().getMoeda())) {
      throw new RegraNegocioException(
          "MOEDA_CONTA_INCOMPATIVEL", "A nova conta deve usar a mesma moeda da conta atual");
    }

    return conta;
  }

  private Categoria resolverCategoria(Transacao transacao, Comando comando) {
    if (comando.categoriaId() == null) {
      return null;
    }
    if (transacao.getCategoria() != null
        && transacao.getCategoria().getId().equals(comando.categoriaId())) {
      return transacao.getCategoria();
    }
    return categoriaRepository
        .buscarAtivaDoUsuario(comando.categoriaId(), comando.usuarioId())
        .orElseThrow(() -> recursoNaoEncontrado("Categoria"));
  }

  private void validarTransacaoSimples(Transacao transacao) {
    if (transacaoRepository.estaVinculadaATransferencia(transacao.getId())
        || transacao.getGrupoRecorrencia() != null
        || transacao.getSegmentoRecorrencia() != null) {
      throw recursoNaoEncontrado("Transacao");
    }
  }

  private void validar(Comando comando) {
    if (comando.escopoInformado()) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO", "Escopo nao se aplica a transacao simples");
    }
    if (comando.resolucaoInformada()) {
      if (comando.repeticao() != null) {
        throw new ValidacaoException(
            "resolucoesSobreposicao", "Conversao em serie nao aceita decisoes de sobreposicao");
      }
      throw new RegraNegocioException(
          "RESOLUCAO_SOBREPOSICAO_INVALIDA", "Resolucao nao se aplica a transacao simples");
    }
    if (comando.contaId() == null) {
      throw new RegraNegocioException("CONTA_OBRIGATORIA", "Conta obrigatoria");
    }
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
        || comando.valor().setScale(4, RoundingMode.UNNECESSARY).precision() > 19) {
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
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrada");
  }

  public record Comando(
      UUID usuarioId,
      String versaoEdicao,
      RepeticaoRecorrencia repeticao,
      boolean escopoInformado,
      boolean resolucaoInformada,
      UUID contaId,
      UUID categoriaId,
      SituacaoTransacao situacao,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira) {}
}
