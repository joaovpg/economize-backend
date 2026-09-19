package com.joaovpg.economize.recorrencia.http.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;
import com.joaovpg.economize.transacao.TipoTransacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CriarRecorrenciaRequest(
    @Schema(
            description =
                "Define o fluxo: RECORRENCIA gera uma série por regra; PARCELAMENTO gera parcelas"
                    + " numeradas.",
            example = "RECORRENCIA",
            required = true)
        @NotNull TipoGrupoRecorrencia tipoGrupo,
    @Schema(
            description = "Conta ativa onde as ocorrências serão projetadas.",
            example = "00000000-0000-0000-0000-000000000001",
            required = true)
        @NotNull UUID contaId,
    @Schema(description = "Categoria opcional das ocorrências.", nullable = true, required = false)
        UUID categoriaId,
    @Schema(
            description = "Natureza financeira; recorrências aceitam somente RECEITA ou DESPESA.",
            example = "DESPESA",
            required = true)
        @NotNull TipoTransacao tipo,
    @Schema(
            description = "Descrição copiada para as ocorrências.",
            example = "Aluguel",
            maxLength = 255,
            required = true)
        @NotBlank String descricao,
    @Schema(
            description = "Observação copiada para as ocorrências.",
            maxLength = 2000,
            nullable = true,
            required = false)
        String observacoes,
    @Schema(
            description =
                "Valor de cada ocorrência ou parcela; deve ser positivo e ter até 4 casas"
                    + " decimais.",
            example = "1800.00",
            minimum = "0.0001",
            format = "double",
            required = true)
        @NotNull @DecimalMin("0.0001") @JsonAlias("valorPorParcela")
        BigDecimal valor,
    @Schema(
            description =
                "Data da primeira ocorrência. Também aceita os aliases dataInicio e"
                    + " dataPrimeiraOcorrencia.",
            example = "2026-01-05",
            format = "date",
            required = true)
        @NotNull @JsonAlias({"dataInicio", "dataPrimeiraOcorrencia"})
        LocalDate inicio,
    @Schema(
            description =
                "Frequência usada para expandir a regra; obrigatória nos dois tipos de grupo.",
            example = "MONTHLY",
            required = true)
        @NotNull FrequenciaRecorrencia frequencia,
    @Schema(
            description = "Intervalo entre ocorrências; padrão 1.",
            example = "1",
            minimum = "1",
            nullable = true,
            required = false)
        Integer intervalo,
    @Schema(
            description =
                "Dias da semana usados principalmente em regras WEEKLY; nulo ou vazio quando não"
                    + " aplicável.",
            example = "[\"MONDAY\"]",
            nullable = true,
            required = false)
        Set<DayOfWeek> diasSemana,
    @Schema(
            description = "Dias do mês da regra. Para MONTHLY, informe ao menos um dia.",
            example = "[5, 20]",
            nullable = true,
            required = false)
        Set<Integer> diasMes,
    @Schema(
            description =
                "Quantidade de ocorrências da RECORRENCIA; é alternativa a ate e não se aplica ao"
                    + " PARCELAMENTO.",
            example = "12",
            minimum = "1",
            nullable = true,
            required = false)
        @JsonAlias("count")
        Integer quantidadeOcorrencias,
    @Schema(
            description = "Data limite da RECORRENCIA; é alternativa a quantidadeOcorrencias.",
            example = "2026-12-31",
            format = "date",
            nullable = true,
            required = false)
        @JsonAlias("dataFim")
        LocalDate ate,
    @Schema(
            description =
                "Número da primeira parcela. Obrigatório para PARCELAMENTO e deve ser positivo.",
            example = "3",
            minimum = "1",
            nullable = true,
            required = false)
        Integer numeroPrimeiraParcela,
    @Schema(
            description =
                "Quantidade total original de parcelas. Obrigatória para PARCELAMENTO e deve ser"
                    + " maior ou igual à primeira parcela.",
            example = "12",
            minimum = "1",
            nullable = true,
            required = false)
        Integer quantidadeTotalOriginal) {}
