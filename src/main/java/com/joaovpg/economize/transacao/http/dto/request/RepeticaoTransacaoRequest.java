package com.joaovpg.economize.transacao.http.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.joaovpg.economize.recorrencia.domain.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.domain.TipoTermino;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Configuração estruturada de repetição ou parcelamento de uma transação.")
public record RepeticaoTransacaoRequest(
    @Schema(description = "Frequência da repetição.", required = true) @NotNull Frequencia frequencia,
    @Schema(
            description = "Intervalo entre ciclos, de 1 a 2147483647.",
            examples = {"1"},
            required = true)
        @NotNull Integer intervalo,
    @Schema(
            description = "Dias da semana; array vazio quando inaplicável, sem duplicatas.",
            required = true)
        @NotNull List<DiaSemana> diasSemana,
    @Schema(
            description = "Dias do mês de 1 a 31; array vazio quando inaplicável, sem duplicatas.",
            required = true)
        @NotNull List<Integer> diasMes,
    @Schema(description = "Política de datas, sempre explícita.", required = true) @NotNull PoliticaDataOcorrencia politicaData,
    @Schema(
            description = "Término da repetição; deve ser nulo quando há parcelamento.",
            nullable = true)
        @JsonProperty(required = true)
        @Valid TerminoTransacaoRequest termino,
    @Schema(
            description = "Numeração das parcelas; deve ser nula quando não há parcelamento.",
            nullable = true)
        @JsonProperty(required = true)
        @Valid ParcelamentoTransacaoRequest parcelamento) {

  @Schema(description = "Frequência de uma repetição ou parcelamento.")
  public enum Frequencia {
    DIARIA,
    SEMANAL,
    MENSAL,
    ANUAL
  }

  @Schema(description = "Dia da semana; a semana começa na segunda-feira.")
  public enum DiaSemana {
    SEGUNDA,
    TERCA,
    QUARTA,
    QUINTA,
    SEXTA,
    SABADO,
    DOMINGO
  }

  @Schema(description = "Término de uma repetição sem parcelamento.")
  public record TerminoTransacaoRequest(
      @Schema(description = "Forma de término.", required = true) @NotNull TipoTermino tipo,
      @Schema(
              description = "Data final inclusiva; apenas para EM_DATA.",
              format = "date",
              nullable = true)
          LocalDate data,
      @Schema(
              description = "Quantidade de ocorrências positiva; apenas para APOS_OCORRENCIAS.",
              nullable = true)
          Integer quantidade) {}

  @Schema(description = "Parcelamento: numeração e total informados por parcela.")
  public record ParcelamentoTransacaoRequest(
      @Schema(description = "Número da primeira parcela desta criação.", required = true) @NotNull Integer numeroPrimeiraParcela,
      @Schema(description = "Total de parcelas do plano.", required = true) @NotNull Integer totalParcelas) {}
}
