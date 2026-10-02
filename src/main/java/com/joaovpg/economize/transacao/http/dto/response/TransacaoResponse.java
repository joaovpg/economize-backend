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
            examples = {"00000000-0000-0000-0000-000000000010"},
            required = true)
        UUID id,
    @Schema(
            description = "Natureza financeira.",
            examples = {"DESPESA"},
            required = true)
        TipoTransacao tipo,
    @Schema(
            description = "Situação atual da transação.",
            examples = {"EFETIVADA"},
            required = true)
        SituacaoTransacao situacao,
    @Schema(
            description = "Descrição exibida no extrato.",
            examples = {"Mercado"},
            required = true)
        String descricao,
    @Schema(description = "Observação, quando informada.", nullable = true) String observacoes,
    @Schema(
            description = "Valor positivo da transação; o sinal é aplicado na consulta do extrato.",
            examples = {"250.75"},
            format = "double",
            required = true)
        BigDecimal valor,
    @Schema(
            description = "Data financeira da operação.",
            examples = {"2026-02-10"},
            format = "date",
            required = true)
        LocalDate dataFinanceira,
    @Schema(
            description = "Instante de efetivação; nulo quando a situação é PLANEJADA.",
            nullable = true,
            format = "date-time")
        Instant efetivadoEm,
    @Schema(
            description = "Conta da transação.",
            examples = {"00000000-0000-0000-0000-000000000001"},
            required = true)
        UUID contaId,
    @Schema(description = "Categoria da transação, quando houver.", nullable = true)
        UUID categoriaId) {}
