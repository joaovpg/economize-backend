package com.joaovpg.economize.recorrencia.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Forma como uma repetição sem parcelamento termina.")
public enum TipoTermino {
  @Schema(description = "Não há data nem quantidade de término.")
  INDEFINIDO,
  @Schema(description = "Termina na data final inclusiva informada.")
  EM_DATA,
  @Schema(description = "Termina após a quantidade de ocorrências informada.")
  APOS_OCORRENCIAS
}
