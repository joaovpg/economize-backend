package com.joaovpg.economize.recorrencia.http.dto.response;

import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;
import com.joaovpg.economize.transacao.TipoTransacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record RecorrenciaResponse(
    @Schema(
            description = "Identificador do segmento retornado.",
            example = "00000000-0000-0000-0000-000000000030")
        UUID id,
    @Schema(
            description = "Identificador do grupo lógico.",
            example = "00000000-0000-0000-0000-000000000031")
        UUID grupoId,
    @Schema(
            description = "Identificador do segmento da regra.",
            example = "00000000-0000-0000-0000-000000000030")
        UUID segmentoId,
    @Schema(description = "RECORRENCIA ou PARCELAMENTO.", example = "RECORRENCIA")
        TipoGrupoRecorrencia tipoGrupo,
    @Schema(description = "Estado do grupo/segmento.", example = "ATIVO") StatusRecorrencia status,
    @Schema(description = "Natureza financeira.", example = "DESPESA") TipoTransacao tipo,
    @Schema(description = "Descrição da regra.", example = "Aluguel") String descricao,
    @Schema(description = "Observação da regra, quando houver.", nullable = true)
        String observacoes,
    @Schema(
            description = "Valor por ocorrência ou parcela.",
            example = "1800.00",
            format = "double")
        BigDecimal valor,
    @Schema(description = "Data da primeira ocorrência.", example = "2026-01-05", format = "date")
        LocalDate inicio,
    @Schema(
            description =
                "Data final de uma recorrência limitada; nula para regra sem data final ou"
                    + " parcelamento.",
            nullable = true,
            format = "date")
        LocalDate fim,
    @Schema(
            description = "Regra RFC 5545 usada para expandir as ocorrências.",
            example = "FREQ=MONTHLY;BYMONTHDAY=5")
        String rrule,
    @Schema(
            description =
                "Quantidade de ocorrências; nula quando a regra termina por data ou não se aplica.",
            nullable = true)
        Integer totalOcorrencias,
    @Schema(
            description = "Primeira parcela; preenchido somente para PARCELAMENTO.",
            nullable = true)
        Integer numeroPrimeiraParcela,
    @Schema(
            description = "Quantidade total original; preenchido somente para PARCELAMENTO.",
            nullable = true)
        Integer quantidadeTotalOriginal,
    @Schema(
            description = "Política de ajuste de datas; parcelamentos usam AJUSTAR_ULTIMO_DIA_MES.",
            example = "PADRAO")
        PoliticaDataOcorrencia politicaDataOcorrencia) {}
