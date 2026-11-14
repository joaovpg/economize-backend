package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;

final class RecorrenciaValidacao {
  private RecorrenciaValidacao() {}

  static RecursoNaoEncontradoException naoEncontrado(String recurso) {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrado");
  }
}
