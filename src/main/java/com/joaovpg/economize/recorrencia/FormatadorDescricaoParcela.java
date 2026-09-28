package com.joaovpg.economize.recorrencia;

import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;

public final class FormatadorDescricaoParcela {
  private FormatadorDescricaoParcela() {}

  public static String formatar(
      String descricao,
      TipoGrupoRecorrencia tipoGrupo,
      Integer numeroParcela,
      Integer quantidadeTotalOriginal) {
    if (tipoGrupo != TipoGrupoRecorrencia.PARCELAMENTO
        || numeroParcela == null
        || numeroParcela < 1
        || quantidadeTotalOriginal == null
        || quantidadeTotalOriginal < 1) {
      return descricao;
    }
    return "%s (%d/%d)".formatted(descricao, numeroParcela, quantidadeTotalOriginal);
  }
}
