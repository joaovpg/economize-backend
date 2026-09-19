package com.joaovpg.economize.transacao;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Natureza da transação financeira.")
public enum TipoTransacao {
  @Schema(description = "Entrada de dinheiro na conta.")
  RECEITA,
  @Schema(description = "Saída de dinheiro da conta.")
  DESPESA
}
