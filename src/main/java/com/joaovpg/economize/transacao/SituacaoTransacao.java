package com.joaovpg.economize.transacao;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Estado de planejamento ou efetivação da transação.")
public enum SituacaoTransacao {
  @Schema(description = "Ainda não efetivada; efetivadoEm permanece nulo.")
  PLANEJADA,
  @Schema(description = "Efetivada; efetivadoEm registra o instante da efetivação.")
  EFETIVADA
}
