package com.joaovpg.economize.transferencia.http.dto.request;

import com.joaovpg.economize.transferencia.SituacaoTransferencia;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CriarTransferenciaRequest(
    @Schema(
            description = "Conta que terá a saída financeira.",
            example = "00000000-0000-0000-0000-000000000001")
        @NotNull UUID contaOrigemId,
    @Schema(
            description = "Conta que terá a entrada financeira; deve ser diferente da origem.",
            example = "00000000-0000-0000-0000-000000000002")
        @NotNull UUID contaDestinoId,
    @Schema(
            description = "Situação aplicada aos dois lados da transferência.",
            example = "EFETIVADA")
        @NotNull SituacaoTransferencia situacao,
    @Schema(
            description = "Descrição exibida nos dois lados do extrato.",
            example = "Reserva",
            maxLength = 255)
        @NotBlank @Size(max = 255) String descricao,
    @Schema(
            description = "Observação opcional copiada para os dois lados.",
            maxLength = 2000,
            nullable = true)
        @Size(max = 2000) String observacoes,
    @Schema(
            description = "Valor positivo movimentado entre as contas, com até 4 casas decimais.",
            example = "500.00",
            minimum = "0.0001",
            format = "double")
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal valor,
    @Schema(
            description = "Data financeira aplicada aos dois lados.",
            example = "2026-03-01",
            format = "date")
        @NotNull LocalDate dataFinanceira) {}
