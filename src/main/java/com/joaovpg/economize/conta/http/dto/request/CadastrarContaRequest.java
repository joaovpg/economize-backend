package com.joaovpg.economize.conta.http.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CadastrarContaRequest(
    @Schema(
            description = "Nome amigável da conta.",
            example = "Conta corrente",
            maxLength = 120,
            required = true)
        @NotBlank String nome,
    @Schema(
            description =
                "Código ISO da moeda da conta. Transferências exigem a mesma moeda nos dois lados.",
            example = "BRL",
            minLength = 3,
            maxLength = 3,
            required = true)
        @NotBlank String moeda,
    @Schema(
            description =
                "Saldo inicial da conta na data informada; aceita valores positivos ou negativos.",
            example = "1500.00",
            format = "double",
            required = true)
        @NotNull BigDecimal saldoInicial,
    @Schema(
            description = "Data a partir da qual o saldo inicial passa a compor o extrato.",
            example = "2026-01-01",
            format = "date",
            required = true)
        @NotNull LocalDate dataSaldoInicial) {}
