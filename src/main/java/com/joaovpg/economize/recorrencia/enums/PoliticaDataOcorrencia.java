package com.joaovpg.economize.recorrencia.enums;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Política usada quando uma ocorrência cai em uma data inexistente do mês.")
public enum PoliticaDataOcorrencia {
  @Schema(description = "Mantém a regra padrão de cálculo da data.")
  PADRAO,
  @Schema(description = "Ajusta para o último dia válido do mês.")
  AJUSTAR_ULTIMO_DIA_MES
}
