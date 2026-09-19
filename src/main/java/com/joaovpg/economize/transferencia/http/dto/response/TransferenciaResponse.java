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
            example = "00000000-0000-0000-0000-000000000020")
        UUID id,
    @Schema(
            description = "Conta de origem da saída.",
            example = "00000000-0000-0000-0000-000000000001")
        UUID contaOrigemId,
    @Schema(
            description = "Conta de destino da entrada.",
            example = "00000000-0000-0000-0000-000000000002")
        UUID contaDestinoId,
    @Schema(description = "Situação aplicada aos dois lados.", example = "EFETIVADA")
        SituacaoTransferencia situacao,
    @Schema(description = "Descrição da transferência.", example = "Reserva") String descricao,
    @Schema(description = "Observação, quando informada.", nullable = true) String observacoes,
    @Schema(description = "Valor positivo da transferência.", example = "500.00", format = "double")
        BigDecimal valor,
    @Schema(
            description = "Data financeira dos dois lados.",
            example = "2026-03-01",
            format = "date")
        LocalDate dataFinanceira,
    @Schema(
            description = "Instante de efetivação; nulo quando PLANEJADA.",
            nullable = true,
            format = "date-time")
        Instant efetivadoEm) {}
