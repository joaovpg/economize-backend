package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/**
 * Bloqueia a transação simples do usuário antes de uma escrita ou exclusão. Quando ela é destacada
 * de uma série, o grupo de origem é bloqueado antes da transação, na mesma ordem das escritas de
 * série, para que a versão comparada depois do bloqueio inclua a revisão atual do grupo.
 */
@ApplicationScoped
public class BloquearTransacaoSimples {
  private final TransacaoRepository transacaoRepository;
  private final GrupoRecorrenciaRepository grupoRepository;

  public BloquearTransacaoSimples(
      TransacaoRepository transacaoRepository, GrupoRecorrenciaRepository grupoRepository) {
    this.transacaoRepository = transacaoRepository;
    this.grupoRepository = grupoRepository;
  }

  public Transacao executar(UUID transacaoId, UUID usuarioId) {
    transacaoRepository
        .buscarGrupoOrigemDoUsuario(transacaoId, usuarioId)
        .ifPresent(
            grupoId ->
                grupoRepository
                    .buscarDoUsuarioParaEdicao(grupoId, usuarioId)
                    .orElseThrow(this::naoEncontrada));
    return transacaoRepository
        .buscarDoUsuarioParaEdicao(transacaoId, usuarioId)
        .orElseThrow(this::naoEncontrada);
  }

  private RecursoNaoEncontradoException naoEncontrada() {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Transacao nao encontrada");
  }
}
