package com.joaovpg.economize.transferencia;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Estado aplicado simultaneamente aos dois lados da transferência.")
public enum SituacaoTransferencia {
  @Schema(description = "Transferência planejada; efetivadoEm permanece nulo.")
  PLANEJADA,
  @Schema(description = "Transferência efetivada; cria impacto confirmado nas duas contas.")
  EFETIVADA
}
