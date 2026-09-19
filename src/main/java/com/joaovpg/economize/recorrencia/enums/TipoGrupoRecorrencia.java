package com.joaovpg.economize.recorrencia.enums;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Tipo de série financeira criada pelo recurso de recorrências.")
public enum TipoGrupoRecorrencia {
  @Schema(description = "Série que se repete conforme uma regra de frequência.")
  RECORRENCIA,
  @Schema(description = "Série finita de parcelas numeradas.")
  PARCELAMENTO
}
