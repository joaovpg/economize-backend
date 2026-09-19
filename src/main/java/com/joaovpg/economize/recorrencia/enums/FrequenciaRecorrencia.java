package com.joaovpg.economize.recorrencia.enums;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Frequência usada para gerar ocorrências pela regra RFC 5545.")
public enum FrequenciaRecorrencia {
  @Schema(description = "A cada dia.")
  DAILY("DAILY"),
  @Schema(description = "A cada semana.")
  WEEKLY("WEEKLY"),
  @Schema(description = "A cada mês.")
  MONTHLY("MONTHLY"),
  @Schema(description = "A cada ano.")
  YEARLY("YEARLY");

  private final String valorRrule;

  FrequenciaRecorrencia(String valorRrule) {
    this.valorRrule = valorRrule;
  }

  public String valorRrule() {
    return valorRrule;
  }
}
