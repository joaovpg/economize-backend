package com.joaovpg.economize.recorrencia.enums;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Escopo de uma alteração ou exclusão de ocorrência recorrente.")
public enum EscopoOcorrencia {
  @Schema(description = "Afeta somente a ocorrência identificada pela data original.")
  ONLY_THIS,
  @Schema(description = "Afeta a ocorrência identificada e todas as ocorrências futuras.")
  THIS_AND_FUTURE
}
