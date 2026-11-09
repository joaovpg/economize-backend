package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.recorrencia.application.RevisarSerieRecorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.TransacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class ExcluirTransacao {
  private final TransacaoRepository transacaoRepository;
  private final RevisarSerieRecorrencia revisarSerie;

  public ExcluirTransacao(
      TransacaoRepository transacaoRepository, RevisarSerieRecorrencia revisarSerie) {
    this.transacaoRepository = transacaoRepository;
    this.revisarSerie = revisarSerie;
  }

  @Transactional
  public void executar(UUID usuarioId, UUID transacaoId, String escopo) {
    var transacao =
        transacaoRepository
            .buscarDoUsuario(transacaoId, usuarioId)
            .orElseThrow(
                () ->
                    new RecursoNaoEncontradoException(
                        "RECURSO_NAO_ENCONTRADO", "Transacao nao encontrada"));

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
    if (transacao.getSegmentoOrigem() != null) {
      revisarSerie.revisar(transacao.getSegmentoOrigem().getGrupo().getId(), usuarioId);
    }
    transacaoRepository.delete(transacao);
  }
}
