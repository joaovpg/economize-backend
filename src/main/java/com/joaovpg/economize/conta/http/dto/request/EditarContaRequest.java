package com.joaovpg.economize.conta.http.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record EditarContaRequest(
    @Schema(description = "Nome amigável da conta.", example = "Conta corrente", maxLength = 120)
        @NotBlank String nome,
    @Schema(
            description = "Código ISO da moeda da conta.",
            example = "BRL",
            minLength = 3,
            maxLength = 3)
        @NotBlank String moeda,
    @Schema(
            description = "Saldo inicial da conta na data informada.",
            example = "1500.00",
            format = "double")
        @NotNull BigDecimal saldoInicial,
    @Schema(description = "Data do saldo inicial.", example = "2026-01-01", format = "date")
        @NotNull LocalDate dataSaldoInicial,
    @Schema(
            description = "Define se a conta fica disponível para novas operações.",
            example = "true")
        @NotNull Boolean ativo) {}
