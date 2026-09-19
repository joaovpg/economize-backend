package com.joaovpg.economize.transacao;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Tipo de elemento que compõe o extrato consolidado.")
public enum OrigemItemConsulta {
  @Schema(description = "Saldo inicial de uma conta dentro do período.")
  SALDO_INICIAL_CONTA,
  @Schema(description = "Transação simples criada diretamente no módulo de transações.")
  TRANSACAO_SIMPLES,
  @Schema(description = "Um dos dois impactos de uma transferência entre contas.")
  TRANSFERENCIA,
  @Schema(description = "Ocorrência gerada por uma recorrência.")
  TRANSACAO_RECORRENTE,
  @Schema(description = "Ocorrência gerada por um parcelamento.")
  PARCELA
}
