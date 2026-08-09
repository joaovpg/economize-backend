package com.joaovpg.economize.categoria.application;

import com.joaovpg.economize.shared.exception.RegraNegocioException;
import java.util.Locale;

final class CategoriaValidation {
  private CategoriaValidation() {}

  static String nome(String nome) {
    if (nome == null || nome.isBlank() || nome.strip().length() > 80) {
      throw new RegraNegocioException("NOME_CATEGORIA_INVALIDO", "Nome da categoria invalido");
    }

    return nome.strip();
  }

  static String cor(String cor) {
    if (cor == null || cor.isBlank()) {
      return null;
    }

    var normalizada = cor.strip().toUpperCase(Locale.ROOT);

    if (!normalizada.matches("#[0-9A-F]{6}")) {
      throw new RegraNegocioException("COR_CATEGORIA_INVALIDA", "Cor da categoria invalida");
    }

    return normalizada;
  }

  public static RegraNegocioException nomeDuplicado() {
    return new RegraNegocioException(
        "NOME_CATEGORIA_DUPLICADO", "Ja existe uma categoria com esse nome no mesmo nivel");
  }
}
