package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/**
 * Incrementa a revisão compartilhada de um grupo de recorrência sob bloqueio pessimista. Deve ser
 * chamado dentro da transação da mutação que altera a série ou uma ocorrência protegida dela.
 */
@ApplicationScoped
public class RevisarSerieRecorrencia {
  private final GrupoRecorrenciaRepository grupoRepository;

  public RevisarSerieRecorrencia(GrupoRecorrenciaRepository grupoRepository) {
    this.grupoRepository = grupoRepository;
  }

  public void revisar(UUID grupoId, UUID usuarioId) {
    var grupo =
        grupoRepository
            .buscarDoUsuarioParaEdicao(grupoId, usuarioId)
            .orElseThrow(() -> RecorrenciaValidacao.naoEncontrado("Recorrencia"));
    grupo.setRevisaoSerie(grupo.getRevisaoSerie() + 1);
  }
}
