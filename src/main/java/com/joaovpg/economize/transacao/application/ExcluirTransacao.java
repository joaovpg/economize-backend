package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.recorrencia.application.RevisarSerieRecorrencia;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class ExcluirTransacao {
  private final TransacaoRepository transacaoRepository;
  private final RevisarSerieRecorrencia revisarSerie;
  private final BloquearTransacaoSimples bloquearTransacao;

  public ExcluirTransacao(
      TransacaoRepository transacaoRepository,
      RevisarSerieRecorrencia revisarSerie,
      BloquearTransacaoSimples bloquearTransacao) {
    this.transacaoRepository = transacaoRepository;
    this.revisarSerie = revisarSerie;
    this.bloquearTransacao = bloquearTransacao;
  }

  @Transactional
  public void executar(UUID usuarioId, UUID transacaoId, String escopo, String versaoEdicao) {
    var transacao = bloquearTransacao.executar(transacaoId, usuarioId);

    if (transacaoRepository.estaVinculadaATransferencia(transacao.getId())
        || transacao.getGrupoRecorrencia() != null
        || transacao.getSegmentoRecorrencia() != null) {
      throw new RegraNegocioException(
          "TRANSACAO_NAO_SIMPLES", "A transacao deve ser manipulada pelo modulo de origem");
    }

    if (escopo != null) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO", "Escopo nao se aplica a transacao simples");
    }
    if (!IdentidadeEdicaoTransacao.versaoSimples(transacao).equals(versaoEdicao)) {
      throw new RegraNegocioException(
          "VERSAO_DESATUALIZADA", "A transacao foi alterada por outra operacao");
    }
    if (transacao.getSegmentoOrigem() != null) {
      revisarSerie.revisar(transacao.getSegmentoOrigem().getGrupo().getId(), usuarioId);
    }
    transacaoRepository.delete(transacao);
    transacaoRepository.flush();
  }
}
