package com.joaovpg.economize.transacao.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Decisão sobre uma sobreposição entre uma nova ocorrência e uma protegida.")
public enum ResolucaoSobreposicao {
  @Schema(description = "Descarta a nova ocorrência e mantém somente a protegida.")
  MANTER_SOMENTE_PRESERVADA,
  @Schema(description = "Mantém a nova ocorrência e a protegida.")
  MANTER_AMBAS
}
