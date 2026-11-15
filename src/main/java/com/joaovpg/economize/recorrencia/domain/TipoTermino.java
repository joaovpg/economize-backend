package com.joaovpg.economize.recorrencia.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Forma como uma repetição termina.")
public enum TipoTermino {
  @Schema(description = "Não há data nem quantidade de término.")
  INDEFINIDO,
  @Schema(description = "Termina na data final inclusiva informada.")
  EM_DATA,
  @Schema(description = "Termina após a quantidade de ocorrências informada, contando a primeira.")
  APOS_OCORRENCIAS,
  @Schema(
      description =
          "Termina na última parcela de um Parcelamento; cria um grupo do tipo PARCELAMENTO.")
  PARCELAS
}
