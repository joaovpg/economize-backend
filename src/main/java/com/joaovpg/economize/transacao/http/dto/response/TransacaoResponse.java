package com.joaovpg.economize.transacao.http.dto.response;

import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record TransacaoResponse(
    @Schema(
            description = "Identificador da transação.",
            example = "00000000-0000-0000-0000-000000000010")
        UUID id,
    @Schema(description = "Natureza financeira.", example = "DESPESA") TipoTransacao tipo,
    @Schema(description = "Situação atual da transação.", example = "EFETIVADA")
        SituacaoTransacao situacao,
    @Schema(description = "Descrição exibida no extrato.", example = "Mercado") String descricao,
    @Schema(description = "Observação, quando informada.", nullable = true) String observacoes,
    @Schema(
            description = "Valor positivo da transação; o sinal é aplicado na consulta do extrato.",
            example = "250.75",
            format = "double")
        BigDecimal valor,
    @Schema(description = "Data financeira da operação.", example = "2026-02-10", format = "date")
        LocalDate dataFinanceira,
    @Schema(
            description = "Instante de efetivação; nulo quando a situação é PLANEJADA.",
            nullable = true,
            format = "date-time")
        Instant efetivadoEm,
    @Schema(description = "Conta da transação.", example = "00000000-0000-0000-0000-000000000001")
        UUID contaId,
    @Schema(description = "Categoria da transação, quando houver.", nullable = true)
        UUID categoriaId) {}
