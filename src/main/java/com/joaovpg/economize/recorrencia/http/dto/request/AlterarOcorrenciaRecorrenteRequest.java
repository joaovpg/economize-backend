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
            examples = {"ONLY_THIS"},
            required = true)
        @NotNull EscopoOcorrencia escopo,
    @Schema(
            description = "Conta ativa da ocorrência.",
            examples = {"00000000-0000-0000-0000-000000000001"},
            required = true)
        @NotNull UUID contaId,
    @Schema(description = "Categoria opcional da ocorrência.", nullable = true) UUID categoriaId,
    @Schema(
            description = "Natureza financeira; somente RECEITA ou DESPESA.",
            examples = {"DESPESA"},
            required = true)
        @NotNull TipoTransacao tipo,
    @Schema(
            description = "Nova descrição da ocorrência.",
            examples = {"Aluguel reajustado"},
            maxLength = 255,
            required = true)
        @NotBlank @Size(max = 255) String descricao,
    @Schema(description = "Nova observação, quando houver.", maxLength = 2000, nullable = true)
        @Size(max = 2000) String observacoes,
    @Schema(
            description = "Novo valor positivo.",
            examples = {"1900.00"},
            minimum = "0.0001",
            format = "double",
            required = true)
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal valor,
    @Schema(
            description = "Nova data financeira da ocorrência.",
            examples = {"2026-04-05"},
            format = "date",
            required = true)
        @NotNull LocalDate dataFinanceira,
    @Schema(
            description = "Nova frequência; quando nula em THIS_AND_FUTURE, mantém a anterior.",
            examples = {"MONTHLY"},
            nullable = true)
        FrequenciaRecorrencia frequencia,
    @Schema(
            description = "Novo intervalo; quando nulo, mantém o anterior.",
            examples = {"1"},
            minimum = "1",
            nullable = true)
        Integer intervalo,
    @Schema(
            description = "Novos dias da semana da regra.",
            examples = {"[\"MONDAY\"]"},
            nullable = true)
        Set<DayOfWeek> diasSemana,
    @Schema(
            description = "Novos dias do mês; obrigatório para frequência MONTHLY.",
            examples = {"[5]"},
            nullable = true)
        Set<Integer> diasMes,
    @Schema(
            description = "Nova quantidade de ocorrências; alternativa a ate e semTermino.",
            examples = {"12"},
            minimum = "1",
            nullable = true)
        Integer quantidadeOcorrencias,
    @Schema(
            description = "Nova data limite; alternativa a quantidadeOcorrencias e semTermino.",
            examples = {"2026-12-31"},
            format = "date",
            nullable = true)
        LocalDate ate,
    @Schema(
            description =
                "Quando true, remove o término da regra; não combine com quantidade ou ate.",
            examples = {"true"},
            nullable = true)
        Boolean semTermino,
    @Schema(
            description =
                "Nova quantidade total original de parcelas. Só vale para PARCELAMENTO e exige"
                    + " THIS_AND_FUTURE.",
            examples = {"15"},
            minimum = "1",
            nullable = true)
        Integer quantidadeTotalOriginal) {}
