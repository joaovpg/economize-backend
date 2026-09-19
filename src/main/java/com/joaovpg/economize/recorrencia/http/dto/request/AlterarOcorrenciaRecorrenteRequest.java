package com.joaovpg.economize.recorrencia.http.dto.request;

import com.joaovpg.economize.recorrencia.enums.EscopoOcorrencia;
import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
import com.joaovpg.economize.transacao.TipoTransacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record AlterarOcorrenciaRecorrenteRequest(
    @Schema(
            description =
                "ONLY_THIS altera apenas a ocorrência; THIS_AND_FUTURE altera a ocorrência e as"
                    + " próximas.",
            example = "ONLY_THIS",
            required = true)
        @NotNull EscopoOcorrencia escopo,
    @Schema(
            description = "Conta ativa da ocorrência.",
            example = "00000000-0000-0000-0000-000000000001",
            required = true)
        @NotNull UUID contaId,
    @Schema(description = "Categoria opcional da ocorrência.", nullable = true, required = false)
        UUID categoriaId,
    @Schema(
            description = "Natureza financeira; somente RECEITA ou DESPESA.",
            example = "DESPESA",
            required = true)
        @NotNull TipoTransacao tipo,
    @Schema(
            description = "Nova descrição da ocorrência.",
            example = "Aluguel reajustado",
            maxLength = 255,
            required = true)
        @NotBlank @Size(max = 255) String descricao,
    @Schema(
            description = "Nova observação, quando houver.",
            maxLength = 2000,
            nullable = true,
            required = false)
        @Size(max = 2000) String observacoes,
    @Schema(
            description = "Novo valor positivo.",
            example = "1900.00",
            minimum = "0.0001",
            format = "double",
            required = true)
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal valor,
    @Schema(
            description = "Nova data financeira da ocorrência.",
            example = "2026-04-05",
            format = "date",
            required = true)
        @NotNull LocalDate dataFinanceira,
    @Schema(
            description = "Nova frequência; quando nula em THIS_AND_FUTURE, mantém a anterior.",
            example = "MONTHLY",
            nullable = true,
            required = false)
        FrequenciaRecorrencia frequencia,
    @Schema(
            description = "Novo intervalo; quando nulo, mantém o anterior.",
            example = "1",
            minimum = "1",
            nullable = true,
            required = false)
        Integer intervalo,
    @Schema(
            description = "Novos dias da semana da regra.",
            example = "[\"MONDAY\"]",
            nullable = true,
            required = false)
        Set<DayOfWeek> diasSemana,
    @Schema(
            description = "Novos dias do mês; obrigatório para frequência MONTHLY.",
            example = "[5]",
            nullable = true,
            required = false)
        Set<Integer> diasMes,
    @Schema(
            description = "Nova quantidade de ocorrências; alternativa a ate e semTermino.",
            example = "12",
            minimum = "1",
            nullable = true,
            required = false)
        Integer quantidadeOcorrencias,
    @Schema(
            description = "Nova data limite; alternativa a quantidadeOcorrencias e semTermino.",
            example = "2026-12-31",
            format = "date",
            nullable = true,
            required = false)
        LocalDate ate,
    @Schema(
            description =
                "Quando true, remove o término da regra; não combine com quantidade ou ate.",
            example = "true",
            nullable = true,
            required = false)
        Boolean semTermino,
    @Schema(
            description =
                "Nova quantidade total original de parcelas. Só vale para PARCELAMENTO e exige"
                    + " THIS_AND_FUTURE.",
            example = "15",
            minimum = "1",
            nullable = true,
            required = false)
        Integer quantidadeTotalOriginal) {}
