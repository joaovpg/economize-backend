package com.joaovpg.economize.transacao.http.dto.request;

import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record AlterarTransacaoRequest(
    @Schema(
            description = "Conta ativa da transação; deve usar a mesma moeda da conta atual.",
            example = "00000000-0000-0000-0000-000000000001",
            required = true)
        @NotNull UUID contaId,
    @Schema(
            description = "Categoria opcional; envie null para remover a categoria.",
            nullable = true,
            required = false)
        UUID categoriaId,
    @Schema(
            description =
                "Situação da transação; ao voltar para PLANEJADA, efetivadoEm é removido.",
            example = "PLANEJADA",
            required = true)
        @NotNull SituacaoTransacao situacao,
    @Schema(description = "Natureza financeira da operação.", example = "DESPESA", required = true)
        @NotNull TipoTransacao tipo,
    @Schema(
            description = "Descrição exibida no extrato.",
            example = "Mercado",
            maxLength = 255,
            required = true)
        @NotBlank @Size(max = 255) String descricao,
    @Schema(
            description = "Observação opcional da operação.",
            example = "Compra do mês",
            maxLength = 2000,
            nullable = true,
            required = false)
        @Size(max = 2000) String observacoes,
    @Schema(
            description = "Valor positivo da operação, com até 4 casas decimais.",
            example = "250.75",
            minimum = "0.0001",
            format = "double",
            required = true)
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal valor,
    @Schema(
            description = "Data financeira da operação.",
            example = "2026-02-10",
            format = "date",
            required = true)
        @NotNull LocalDate dataFinanceira) {}
