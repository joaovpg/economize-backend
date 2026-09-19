package com.joaovpg.economize.conta.http.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ContaResponse(
    @Schema(
            description = "Identificador da conta.",
            example = "00000000-0000-0000-0000-000000000001")
        UUID id,
    @Schema(description = "Nome amigável da conta.", example = "Conta corrente") String nome,
    @Schema(description = "Código ISO da moeda.", example = "BRL") String moeda,
    @Schema(description = "Saldo inicial registrado.", example = "1500.00", format = "double")
        BigDecimal saldoInicial,
    @Schema(description = "Data do saldo inicial.", example = "2026-01-01", format = "date")
        LocalDate dataSaldoInicial,
    @Schema(description = "Indica se a conta está ativa.", example = "true") boolean ativo) {}
