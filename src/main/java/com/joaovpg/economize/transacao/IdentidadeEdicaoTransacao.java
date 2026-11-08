package com.joaovpg.economize.transacao;

import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

public final class IdentidadeEdicaoTransacao {
  private static final String PREFIXO = "1:s:";
  private static final String PREFIXO_RECORRENCIA = "1:r:";

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

  public static String chaveRecorrencia(UUID segmentoId, LocalDate dataOriginal) {
    return codificar(PREFIXO_RECORRENCIA + segmentoId + ":" + dataOriginal);
  }

  /** Indica se a chave identifica uma ocorrência de série (segmento e data original). */
  public static boolean ehChaveDeOcorrencia(String chave) {
    var valor = decodificarOuNulo(chave);
    return valor != null && valor.startsWith(PREFIXO_RECORRENCIA);
  }

  /**
   * Identifica uma ocorrência de série pela chave. Chaves malformadas ou que não sejam de
   * ocorrência resultam em não encontrado, sem revelar propriedade.
   */
  public static ChaveOcorrencia identificarOcorrencia(String chave) {
    var valor = decodificarOuNulo(chave);
    if (valor == null || !valor.startsWith(PREFIXO_RECORRENCIA)) {
      throw naoEncontrada();
    }
    var partes = valor.substring(PREFIXO_RECORRENCIA.length()).split(":", 2);
    if (partes.length != 2) {
      throw naoEncontrada();
    }
    try {
      var segmentoId = UUID.fromString(partes[0]);
      var dataOriginal = LocalDate.parse(partes[1]);
      if (!chaveRecorrencia(segmentoId, dataOriginal).equals(chave)) {
        throw naoEncontrada();
      }
      return new ChaveOcorrencia(segmentoId, dataOriginal);
    } catch (IllegalArgumentException | java.time.DateTimeException e) {
      throw naoEncontrada();
    }
  }

  /** Segmento e data original que identificam uma ocorrência de série. */
  public record ChaveOcorrencia(UUID segmentoId, LocalDate dataOriginal) {}

  public static String versao(long versao) {
    return codificar("1:v:" + versao);
  }

  /**
   * Versão de uma ocorrência de série: revisão compartilhada do grupo e, quando a ocorrência já
   * estiver materializada, a versão do registro selecionado.
   */
  public static String versaoRecorrencia(long revisaoSerie, Long versaoRegistro) {
    var registro = versaoRegistro == null ? "" : versaoRegistro.toString();
    return codificar("1:g:" + revisaoSerie + ":" + registro);
  }

  private static String codificar(String valor) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(valor.getBytes(StandardCharsets.UTF_8));
  }

  private static String decodificarOuNulo(String chave) {
    if (chave == null) {
      return null;
    }
    try {
      return new String(Base64.getUrlDecoder().decode(chave), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static RecursoNaoEncontradoException naoEncontrada() {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Transacao nao encontrada");
  }
}
