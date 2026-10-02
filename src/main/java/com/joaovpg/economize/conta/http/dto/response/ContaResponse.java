package com.joaovpg.economize.conta.http.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ContaResponse(
    @Schema(
            description = "Identificador da conta.",
            examples = {"00000000-0000-0000-0000-000000000001"},
            required = true)
        UUID id,
    @Schema(
            description = "Nome amigável da conta.",
            examples = {"Conta corrente"},
            required = true)
        String nome,
    @Schema(
            description = "Código ISO da moeda.",
            examples = {"BRL"},
            required = true)
        String moeda,
    @Schema(
            description = "Saldo inicial registrado.",
            examples = {"1500.00"},
            format = "double",
            required = true)
        BigDecimal saldoInicial,
    @Schema(
            description = "Data do saldo inicial.",
            examples = {"2026-01-01"},
            format = "date",
            required = true)
        LocalDate dataSaldoInicial,
    @Schema(
            description = "Indica se a conta está ativa.",
            examples = {"true"},
            required = true)
        boolean ativo) {}
