package com.joaovpg.economize.categoria.application;

import com.joaovpg.economize.categoria.CategoriaRepository;

final class CategoriaConstraintHandler {

  private CategoriaConstraintHandler() {}

  static void flush(CategoriaRepository repository) {
    try {
      repository.flush();
    } catch (org.hibernate.exception.ConstraintViolationException exception) {
      var constraint = exception.getConstraintName();

      if ("uk003_01_nome_raiz".equalsIgnoreCase(constraint)
          || "uk003_02_nome_irma".equalsIgnoreCase(constraint)) {
        throw CategoriaValidation.nomeDuplicado();
      }

      throw exception;
    }
  }
}
