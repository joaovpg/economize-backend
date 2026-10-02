package com.joaovpg.economize.transferencia.http.dto.response;

import com.joaovpg.economize.transferencia.SituacaoTransferencia;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record TransferenciaResponse(
    @Schema(
            description = "Identificador da transferência.",
            examples = {"00000000-0000-0000-0000-000000000020"},
            required = true)
        UUID id,
    @Schema(
            description = "Conta de origem da saída.",
            examples = {"00000000-0000-0000-0000-000000000001"},
            required = true)
        UUID contaOrigemId,
    @Schema(
            description = "Conta de destino da entrada.",
            examples = {"00000000-0000-0000-0000-000000000002"},
            required = true)
        UUID contaDestinoId,
    @Schema(
            description = "Situação aplicada aos dois lados.",
            examples = {"EFETIVADA"},
            required = true)
        SituacaoTransferencia situacao,
    @Schema(
            description = "Descrição da transferência.",
            examples = {"Reserva"},
            required = true)
        String descricao,
    @Schema(description = "Observação, quando informada.", nullable = true) String observacoes,
    @Schema(
            description = "Valor positivo da transferência.",
            examples = {"500.00"},
            format = "double",
            required = true)
        BigDecimal valor,
    @Schema(
            description = "Data financeira dos dois lados.",
            examples = {"2026-03-01"},
            format = "date",
            required = true)
        LocalDate dataFinanceira,
    @Schema(
            description = "Instante de efetivação; nulo quando PLANEJADA.",
            nullable = true,
            format = "date-time")
        Instant efetivadoEm) {}
