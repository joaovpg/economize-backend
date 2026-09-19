package com.joaovpg.economize.recorrencia.enums;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Estado do grupo ou segmento de recorrência.")
public enum StatusRecorrencia {
  @Schema(description = "Regra ativa e apta a gerar ocorrências.")
  ATIVO,
  @Schema(description = "Regra concluída porque atingiu seu término.")
  CONCLUIDO,
  @Schema(description = "Regra cancelada ou interrompida.")
  CANCELADO
}
