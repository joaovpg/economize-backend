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
            examples = {"00000000-0000-0000-0000-000000000030"},
            required = true)
        UUID id,
    @Schema(
            description = "Identificador do grupo lógico.",
            examples = {"00000000-0000-0000-0000-000000000031"},
            required = true)
        UUID grupoId,
    @Schema(
            description = "Identificador do segmento da regra.",
            examples = {"00000000-0000-0000-0000-000000000030"},
            required = true)
        UUID segmentoId,
    @Schema(
            description = "RECORRENCIA ou PARCELAMENTO.",
            examples = {"RECORRENCIA"},
            required = true)
        TipoGrupoRecorrencia tipoGrupo,
    @Schema(
            description = "Estado do grupo/segmento.",
            examples = {"ATIVO"},
            required = true)
        StatusRecorrencia status,
    @Schema(
            description = "Natureza financeira.",
            examples = {"DESPESA"},
            required = true)
        TipoTransacao tipo,
    @Schema(
            description = "Descrição da regra.",
            examples = {"Aluguel"},
            required = true)
        String descricao,
    @Schema(description = "Observação da regra, quando houver.", nullable = true)
        String observacoes,
    @Schema(
            description = "Valor por ocorrência ou parcela.",
            examples = {"1800.00"},
            format = "double",
            required = true)
        BigDecimal valor,
    @Schema(
            description = "Data da primeira ocorrência.",
            examples = {"2026-01-05"},
            format = "date",
            required = true)
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
            examples = {"FREQ=MONTHLY;BYMONTHDAY=5"},
            required = true)
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
            examples = {"PADRAO"},
            required = true)
        PoliticaDataOcorrencia politicaDataOcorrencia) {}
