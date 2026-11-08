package com.joaovpg.economize.transacao.http.dto.response;

import com.joaovpg.economize.transacao.ResolucaoSobreposicao;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse.ItemResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Prévia de alteração: impactos e sobreposições, sem gravação.")
public record PreviaEdicaoTransacaoResponse(
    @Schema(description = "Versão analisada; igual à versão enviada.", required = true)
        String versaoEdicao,
    @Schema(description = "Período analisado.", required = true) PeriodoResponse periodo,
    @Schema(description = "Política geral informada.", required = true)
        ResolucaoSobreposicao politicaSobreposicao,
    @Schema(description = "Ocorrências protegidas do período, no formato de leitura.")
        List<ItemResponse> itensPreservados,
    @Schema(description = "Candidatas da nova regra com data financeira no período.")
        List<CandidataResponse> ocorrenciasNovas,
    @Schema(description = "Candidatas que colidem com protegidas no período.")
        List<SobreposicaoResponse> sobreposicoes,
    @Schema(
            description =
                "Contagem exata das candidatas que colidem com protegidas fora do período.",
            minimum = "0")
        long quantidadeSobreposicoesForaPeriodo) {

  public record PeriodoResponse(YearMonth inicio, YearMonth fim) {}

  public record CandidataResponse(
      LocalDate dataOriginal,
      LocalDate dataFinanceira,
      BigDecimal valor,
      @Schema(nullable = true) Integer numeroParcela) {}

  public record SobreposicaoResponse(
      LocalDate dataOriginalCandidata,
      LocalDate dataFinanceira,
      List<String> chavesPreservadas,
      ResolucaoSobreposicao resolucaoSugerida,
      List<ResolucaoSobreposicao> resolucoesPermitidas) {}
}
