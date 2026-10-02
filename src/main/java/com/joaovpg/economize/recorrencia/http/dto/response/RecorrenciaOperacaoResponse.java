package com.joaovpg.economize.recorrencia.http.dto.response;

import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record RecorrenciaOperacaoResponse(
    @Schema(
            description =
                "Identificador da transação materializada; nulo quando o retorno representa"
                    + " segmento virtual.",
            nullable = true)
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
            description = "Situação da transação; nula quando a ocorrência ainda é virtual.",
            nullable = true)
        SituacaoTransacao situacao,
    @Schema(
            description = "Descrição retornada.",
            examples = {"Aluguel"},
            required = true)
        String descricao,
    @Schema(description = "Observação, quando houver.", nullable = true) String observacoes,
    @Schema(
            description = "Valor positivo da transação ou ocorrência.",
            examples = {"1800.00"},
            format = "double",
            required = true)
        BigDecimal valor,
    @Schema(
            description = "Data financeira efetiva.",
            examples = {"2026-04-05"},
            format = "date",
            required = true)
        LocalDate dataFinanceira,
    @Schema(
            description =
                "Instante de efetivação; nulo quando a ocorrência continua planejada/virtual.",
            nullable = true,
            format = "date-time")
        Instant efetivadoEm,
    @Schema(
            description = "Conta da ocorrência.",
            examples = {"00000000-0000-0000-0000-000000000001"},
            required = true)
        UUID contaId,
    @Schema(description = "Categoria, quando houver.", nullable = true) UUID categoriaId,
    @Schema(
            description = "Data original usada para identificar a ocorrência.",
            examples = {"2026-04-05"},
            format = "date",
            required = true)
        LocalDate dataOriginalRecorrencia,
    @Schema(description = "Número da parcela; nulo para recorrências comuns.", nullable = true)
        Integer numeroParcela,
    @Schema(description = "Total de parcelas; nulo para recorrências comuns.", nullable = true)
        Integer totalParcelas,
    @Schema(
            description = "Regra RFC 5545 do segmento.",
            examples = {"FREQ=MONTHLY;BYMONTHDAY=5"},
            required = true)
        String rrule,
    @Schema(
            description = "Início do segmento de recorrência.",
            examples = {"2026-01-05"},
            format = "date",
            required = true)
        LocalDate inicioRecorrencia,
    @Schema(
            description = "Política de ajuste de datas.",
            examples = {"PADRAO"},
            required = true)
        PoliticaDataOcorrencia politicaDataOcorrencia) {}
