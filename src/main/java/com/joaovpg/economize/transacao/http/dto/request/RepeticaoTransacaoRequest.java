package com.joaovpg.economize.transacao.http.dto.request;

import com.joaovpg.economize.recorrencia.domain.AncoragemRecorrencia;
import com.joaovpg.economize.recorrencia.domain.TipoTermino;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
    description =
        "Repetição ou parcelamento de uma transação. Campos desconhecidos são rejeitados.")
public record RepeticaoTransacaoRequest(
    @Schema(description = "Frequência da repetição.", required = true) @NotNull Frequencia frequencia,
    @Schema(
            description = "Intervalo entre ciclos, de 1 a 2147483647 (a cada X).",
            examples = {"1"},
            required = true)
        @NotNull Integer intervalo,
    @Schema(
            description =
                "Âncora da repetição. DIA_UTIL só vale com frequência MENSAL e data financeira em"
                    + " dia útil.",
            required = true)
        @NotNull AncoragemRecorrencia ancoragem,
    @Schema(description = "Término da repetição ou do Parcelamento.", required = true)
        @NotNull @Valid TerminoTransacaoRequest termino) {

  @Schema(description = "Frequência de uma repetição ou parcelamento.")
  public enum Frequencia {
    DIARIA,
    SEMANAL,
    MENSAL,
    ANUAL
  }

  @Schema(
      description =
          "Término. Cada tipo aceita somente os próprios campos: data em EM_DATA, quantidade em"
              + " APOS_OCORRENCIAS e numeração em PARCELAS.")
  public record TerminoTransacaoRequest(
      @Schema(description = "Forma de término.", required = true) @NotNull TipoTermino tipo,
      @Schema(
              description = "Data final inclusiva; apenas para EM_DATA.",
              format = "date",
              nullable = true)
          LocalDate data,
      @Schema(
              description =
                  "Quantidade de ocorrências positiva, contando a primeira; apenas para"
                      + " APOS_OCORRENCIAS.",
              nullable = true)
          Integer quantidade,
      @Schema(
              description = "Número da primeira parcela desta criação; apenas para PARCELAS.",
              nullable = true)
          Integer numeroPrimeiraParcela,
      @Schema(description = "Total de parcelas do plano; apenas para PARCELAS.", nullable = true)
          Integer totalParcelas) {}
}
