package com.joaovpg.economize.transacao.http.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Alteração proposta para a prévia: a representação completa do PUT, sem política e resoluções de
 * sobreposição, que pertencem à própria operação de alteração.
 */
@Schema(
    description =
        "Representação completa da alteração, igual ao PUT, sem politicaSobreposicao e sem"
            + " resolucoesSobreposicao.")
public record AlteracaoPreviaTransacaoRequest(
    @Schema(
            description = "Conta ativa da transação; deve usar a mesma moeda da conta atual.",
            examples = {"00000000-0000-0000-0000-000000000001"},
            required = true)
        @NotNull UUID contaId,
    @Schema(
            description = "Categoria opcional; envie null para remover a categoria.",
            nullable = true)
        @JsonProperty(required = true)
        UUID categoriaId,
    @Schema(
            description = "Situação da transação.",
            examples = {"PLANEJADA"},
            required = true)
        @NotNull SituacaoTransacao situacao,
    @Schema(
            description = "Natureza financeira da operação.",
            examples = {"DESPESA"},
            required = true)
        @NotNull TipoTransacao tipo,
    @Schema(
            description = "Descrição exibida no extrato.",
            examples = {"Mercado"},
            maxLength = 255,
            required = true)
        @NotBlank @Size(max = 255) String descricao,
    @Schema(
            description = "Observação opcional da operação.",
            examples = {"Compra do mês"},
            maxLength = 2000,
            nullable = true)
        @JsonProperty(required = true)
        @Size(max = 2000) @Pattern(regexp = "(?s).*\\S.*")
        String observacoes,
    @Schema(
            description = "Valor positivo da operação, com até 4 casas decimais.",
            examples = {"250.75"},
            minimum = "0.0001",
            format = "double",
            required = true)
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal valor,
    @Schema(
            description = "Data financeira da operação.",
            examples = {"2026-02-10"},
            format = "date",
            required = true)
        @NotNull LocalDate dataFinanceira,
    @Schema(description = "Versão opaca obtida na consulta ou escrita anterior.", required = true)
        @NotBlank String versaoEdicao,
    @Schema(
            description = "Repetição ou parcelamento proposto; null representa transação simples.",
            nullable = true)
        @JsonProperty(required = true)
        @Valid RepeticaoTransacaoRequest repeticao,
    @Schema(
            description =
                "Obrigatório para ocorrência de série (ESTA_E_AS_PROXIMAS); deve ser omitido para"
                    + " transação simples.")
        JsonNode escopo) {
  /** Indica se o campo escopo veio no corpo, mesmo que nulo. */
  public boolean escopoInformado() {
    return escopo != null;
  }
}
