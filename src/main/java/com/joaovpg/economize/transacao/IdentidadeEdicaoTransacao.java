package com.joaovpg.economize.transacao;

import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class IdentidadeEdicaoTransacao {
  private static final String PREFIXO = "1:s:";

  private IdentidadeEdicaoTransacao() {}

  public static String chave(UUID id) {
    return codificar(PREFIXO + id);
  }

  public static UUID identificar(String chave) {
    try {
      var valor = new String(Base64.getUrlDecoder().decode(chave), StandardCharsets.UTF_8);
      if (!valor.startsWith(PREFIXO)) {
        throw new IllegalArgumentException();
      }
      var id = UUID.fromString(valor.substring(PREFIXO.length()));
      if (!chave(id).equals(chave)) {
        throw new IllegalArgumentException();
      }
      return id;
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Transacao nao encontrada");
    }
  }

  public static String versao(long versao) {
    return codificar("1:v:" + versao);
  }

  private static String codificar(String valor) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(valor.getBytes(StandardCharsets.UTF_8));
  }
}
