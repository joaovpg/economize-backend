package com.joaovpg.economize.recorrencia.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Âncora de uma repetição: onde cai cada ocorrência dentro do mês ou do ano.")
public enum AncoragemRecorrencia {
  @Schema(
      description =
          "Mesmo dia do mês da data financeira. Em mensal e anual, usa o último dia quando o mês"
              + " é mais curto.")
  DIA_DO_MES,
  @Schema(
      description =
          "N-ésimo dia útil, de segunda a sexta, do mês da data financeira. Vale só para"
              + " frequência mensal; N é calculado pelo servidor e não pode passar de 20.")
  DIA_UTIL
}
