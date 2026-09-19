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

public record CriarTransacaoRequest(
    @Schema(description = "Conta ativa que recebe a transação.", example = "00000000-0000-0000-0000-000000000001")
    @NotNull UUID contaId,
    @Schema(description = "Categoria opcional da transação.", nullable = true)
    UUID categoriaId,
    @Schema(description = "Situação da transação; EFETIVADA preenche efetivadoEm.", example = "EFETIVADA")
    @NotNull SituacaoTransacao situacao,
    @Schema(description = "Natureza financeira da operação.", example = "DESPESA")
    @NotNull TipoTransacao tipo,
    @Schema(description = "Descrição exibida no extrato.", example = "Mercado", maxLength = 255)
    @NotBlank @Size(max = 255) String descricao,
    @Schema(description = "Observação opcional da operação.", example = "Compra do mês", maxLength = 2000, nullable = true)
    @Size(max = 2000) String observacoes,
    @Schema(
        description = "Valor positivo da operação, com até 15 dígitos inteiros e 4 casas decimais.",
        example = "250.75",
        minimum = "0.0001",
        format = "double")
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal valor,
    @Schema(description = "Data financeira da operação.", example = "2026-02-10", format = "date")
    @NotNull LocalDate dataFinanceira) {}
