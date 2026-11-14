package com.joaovpg.economize.transacao.http.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.joaovpg.economize.recorrencia.domain.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.domain.TipoTermino;
import com.joaovpg.economize.transacao.domain.EscopoEdicaoTransacao;
import com.joaovpg.economize.transacao.domain.OrigemItemConsulta;
import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ConsultaTransacoesResponse(
    @Schema(
            description = "Primeiro mês consultado, inclusivo.",
            type = SchemaType.STRING,
            pattern = "\\d{4}-(0[1-9]|1[0-2])",
            examples = {"\"2026-01\""},
            required = true)
        YearMonth inicio,
    @Schema(
            description = "Último mês consultado, inclusivo.",
            type = SchemaType.STRING,
            pattern = "\\d{4}-(0[1-9]|1[0-2])",
            examples = {"\"2026-03\""},
            required = true)
        YearMonth fim,
    @Schema(
            description =
                "Saldo imediatamente antes do primeiro dia do período, já considerando saldos"
                    + " iniciais e impactos anteriores dos filtros informados.",
            examples = {"1250.0000"},
            format = "double",
            required = true)
        BigDecimal saldoAbertura,
    @Schema(
            description =
                "Itens ordenados por data financeira e critérios de desempate do extrato.",
            required = true)
        List<ItemResponse> itens) {

  public record ItemResponse(
      @Schema(
              description =
                  "Origem do item: SALDO_INICIAL_CONTA, TRANSACAO_SIMPLES, TRANSFERENCIA, "
                      + "TRANSACAO_RECORRENTE ou PARCELA.",
              examples = {"TRANSACAO_RECORRENTE"},
              required = true)
          OrigemItemConsulta origem,
      @Schema(
              description =
                  "Identificador da operação. Em ocorrência recorrente ainda virtual, pode ser"
                      + " nulo; em transferência identifica a transferência, não cada lado.",
              nullable = true)
          UUID operacaoId,
      @Schema(
              description =
                  "Situação da transação; nula para SALDO_INICIAL_CONTA e ocorrência virtual.",
              nullable = true)
          SituacaoTransacao situacao,
      @Schema(
              description = "Descrição exibida no extrato.",
              examples = {"Aluguel"},
              required = true)
          String descricao,
      @Schema(description = "Observação, quando houver.", nullable = true) String observacoes,
      @Schema(
              description =
                  "Valor com sinal de impacto na conta: receitas/entradas positivas,"
                      + " despesas/saídas negativas.",
              examples = {"-1800.0000"},
              format = "double",
              required = true)
          BigDecimal valor,
      @Schema(
              description = "Data financeira que determina a ordenação do item.",
              examples = {"2026-02-05"},
              format = "date",
              required = true)
          LocalDate dataFinanceira,
      @Schema(
              description =
                  "Instante da efetivação; nulo para itens planejados, virtuais ou saldo inicial.",
              nullable = true,
              format = "date-time")
          Instant efetivadoEm,
      @Schema(
              description = "Conta cujo saldo é impactado.",
              examples = {"00000000-0000-0000-0000-000000000001"},
              required = true)
          UUID contaId,
      @Schema(
              description =
                  "Categoria do item, quando houver; saldo inicial e transferência não têm"
                      + " categoria.",
              nullable = true)
          UUID categoriaId,
      @Schema(
              description =
                  "Conta do outro lado da transferência; preenchido somente para TRANSFERENCIA.",
              nullable = true)
          UUID contaContraparteId,
      @Schema(
              description =
                  "Chave opaca para edição; nula para saldo inicial e transferência, que são"
                      + " editadas pela sua operação.",
              nullable = true)
          String chaveEdicao,
      @Schema(
              description = "Versão opaca necessária para editar o item; nula para saldo inicial.",
              nullable = true)
          String versaoEdicao,
      @Schema(
              description = "Tipo de transação, quando aplicável; nulo para transferência.",
              nullable = true)
          TipoTransacao tipo,
      @Schema(description = "Indica ocorrência virtual; nulo para saldo inicial.", nullable = true)
          Boolean virtual,
      @Schema(
              description =
                  "Indica item individualizado ou efetivado que não muda em lote; nulo para saldo"
                      + " inicial.",
              nullable = true)
          Boolean protegida,
      @Schema(description = "Metadados da série; nulo fora de séries.", nullable = true)
          SerieResponse serie,
      @Schema(
              description =
                  "Configuração estruturada da repetição do segmento; nula fora de séries.",
              nullable = true)
          RepeticaoResponse repeticao,
      @Schema(
              description = "Escopos de edição aceitos para o item; lista vazia fora de séries.",
              required = true)
          List<EscopoEdicaoTransacao> escoposDisponiveis) {}

  public record SerieResponse(
      @Schema(description = "Grupo da série.", required = true) UUID grupoId,
      @Schema(description = "Segmento que gera a ocorrência.", required = true) UUID segmentoId,
      @Schema(description = "Data de início real do segmento.", format = "date", required = true)
          LocalDate inicioSegmento,
      @Schema(
              description = "Data original que identifica a ocorrência no segmento.",
              format = "date",
              required = true)
          LocalDate dataOriginal,
      @Schema(description = "Número da parcela; nulo sem parcelamento.", nullable = true)
          Integer numeroParcela,
      @Schema(description = "Total contratado de parcelas; nulo sem parcelamento.", nullable = true)
          Integer totalParcelas) {}

  public record RepeticaoResponse(
      @Schema(description = "Frequência da repetição.", required = true)
          FrequenciaRepeticao frequencia,
      @Schema(description = "Intervalo entre ciclos da frequência.", required = true) int intervalo,
      @Schema(
              description =
                  "Dias da semana em ordem de segunda a domingo; vazio quando inaplicável.",
              required = true)
          List<DiaSemanaRepeticao> diasSemana,
      @Schema(
              description = "Dias do mês em ordem crescente; vazio quando inaplicável.",
              required = true)
          List<Integer> diasMes,
      @Schema(description = "Política aplicada às datas inexistentes.", required = true)
          PoliticaDataOcorrencia politicaData,
      @Schema(description = "Término da repetição; nulo quando há parcelamento.", nullable = true)
          TerminoResponse termino,
      @Schema(description = "Numeração do parcelamento; nula sem parcelamento.", nullable = true)
          ParcelamentoResponse parcelamento) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record TerminoResponse(
      @Schema(description = "Tipo do término.", required = true) TipoTermino tipo,
      @Schema(
              description = "Data final inclusiva; presente somente para EM_DATA.",
              format = "date",
              nullable = true)
          LocalDate data,
      @Schema(
              description = "Quantidade de ocorrências; presente somente para APOS_OCORRENCIAS.",
              nullable = true)
          Integer quantidade) {}

  public record ParcelamentoResponse(
      @Schema(description = "Número da primeira parcela do plano.", required = true)
          int numeroPrimeiraParcela,
      @Schema(description = "Total contratado original de parcelas.", required = true)
          int totalParcelas) {}

  @Schema(description = "Frequência de uma repetição, em português.")
  public enum FrequenciaRepeticao {
    @Schema(description = "A cada dia.")
    DIARIA,
    @Schema(description = "A cada semana.")
    SEMANAL,
    @Schema(description = "A cada mês.")
    MENSAL,
    @Schema(description = "A cada ano.")
    ANUAL
  }

  @Schema(description = "Dia da semana, em português.")
  public enum DiaSemanaRepeticao {
    SEGUNDA,
    TERCA,
    QUARTA,
    QUINTA,
    SEXTA,
    SABADO,
    DOMINGO
  }
}
