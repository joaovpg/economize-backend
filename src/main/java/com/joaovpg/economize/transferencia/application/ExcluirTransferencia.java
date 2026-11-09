package com.joaovpg.economize.transferencia.application;

import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.transferencia.TransferenciaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class ExcluirTransferencia {
  private final TransferenciaRepository transferenciaRepository;
  private final TransacaoRepository transacaoRepository;

  public ExcluirTransferencia(
      TransferenciaRepository transferenciaRepository, TransacaoRepository transacaoRepository) {
    this.transferenciaRepository = transferenciaRepository;
    this.transacaoRepository = transacaoRepository;
  }

  @Transactional
  public void executar(UUID usuarioId, UUID transferenciaId, String versaoEdicao) {
    var transferencia =
        transferenciaRepository
            .buscarDoUsuarioParaEdicao(transferenciaId, usuarioId)
            .orElseThrow(
                () ->
                    new RecursoNaoEncontradoException(
                        "RECURSO_NAO_ENCONTRADO", "Transferencia nao encontrada"));

    if (!IdentidadeEdicaoTransacao.versao(transferencia.getVersao()).equals(versaoEdicao)) {
      throw new RegraNegocioException(
          "VERSAO_DESATUALIZADA", "A transferencia foi alterada por outra operacao");
    }

    var saida = transferencia.getTransacaoSaida();
    var entrada = transferencia.getTransacaoEntrada();

    transferenciaRepository.delete(transferencia);
    transferenciaRepository.flush();
    transacaoRepository.delete(saida);
    transacaoRepository.delete(entrada);
    transacaoRepository.flush();
  }
}
