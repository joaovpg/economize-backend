package com.joaovpg.economize.transacao.http.dto.request;

import com.joaovpg.economize.transacao.domain.ResolucaoSobreposicao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Prévia de uma alteração com sobreposições, sem gravação.")
public record PreviaEdicaoTransacaoRequest(
    @Schema(description = "Alteração proposta.", required = true) @NotNull @Valid AlteracaoPreviaTransacaoRequest alteracao,
    @Schema(
            description = "Período de análise em meses civis inclusivos, de 1 a 12.",
            required = true)
        @NotNull @Valid Periodo periodo,
    @Schema(
            description =
                "Política geral para sobreposições sem escolha específica. Nesta operação, aceita"
                    + " somente MANTER_SOMENTE_PRESERVADA.",
            required = true)
        @NotNull ResolucaoSobreposicao politicaSobreposicao) {

  @Schema(description = "Período de meses inclusivos no formato AAAA-MM.")
  public record Periodo(
      @Schema(
              description = "Mês inicial.",
              required = true,
              examples = {"2026-11"})
          @NotNull YearMonth inicio,
      @Schema(
              description = "Mês final.",
              required = true,
              examples = {"2027-10"})
          @NotNull YearMonth fim) {}
}
