package com.joaovpg.economize.transacao.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Alcance de uma edição sobre ocorrência de uma série.")
public enum EscopoEdicaoTransacao {
  @Schema(description = "Afeta somente a ocorrência selecionada.")
  SOMENTE_ESTA,
  @Schema(description = "Afeta a ocorrência selecionada e todas as próximas da série.")
  ESTA_E_AS_PROXIMAS
}
