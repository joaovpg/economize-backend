package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.application.CriarSerieTransacao;
import com.joaovpg.economize.recorrencia.application.ResolverOcorrenciasRecorrentes;
import com.joaovpg.economize.recorrencia.application.RevisarSerieRecorrencia;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AlterarTransacao {
  private final TransacaoRepository transacaoRepository;
  private final CriarSerieTransacao criarSerie;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;
  private final RevisarSerieRecorrencia revisarSerie;
  private final RegrasEdicaoTransacao regras;

  public AlterarTransacao(
      TransacaoRepository transacaoRepository,
      CriarSerieTransacao criarSerie,
      ResolverOcorrenciasRecorrentes resolverOcorrencias,
      RevisarSerieRecorrencia revisarSerie,
      RegrasEdicaoTransacao regras) {
    this.transacaoRepository = transacaoRepository;
    this.criarSerie = criarSerie;
    this.resolverOcorrencias = resolverOcorrencias;
    this.revisarSerie = revisarSerie;
    this.regras = regras;
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

    var conta = regras.resolverConta(transacao.getConta(), comando.contaId(), comando.usuarioId());
    regras.validarMesmaMoeda(transacao.getConta(), conta);
    var categoria =
        regras.resolverCategoria(
            transacao.getCategoria(), comando.categoriaId(), comando.usuarioId());
    regras.validarDatas(
        conta, transacao.getUsuario(), comando.dataFinanceira(), comando.situacao());

    if (transacao.getSegmentoOrigem() != null) {
      revisarSerie.revisar(transacao.getSegmentoOrigem().getGrupo().getId(), comando.usuarioId());
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
    transacao.setSegmentoOrigem(null);
    transacao.setDataOriginalOrigem(null);
    transacao.setNumeroParcelaOrigem(null);
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
    regras.validarCamposComuns(
        comando.contaId(),
        comando.situacao(),
        comando.tipo(),
        comando.valor(),
        comando.descricao(),
        comando.observacoes(),
        comando.dataFinanceira());
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
