package com.joaovpg.economize.transacao.http.dto.response;

import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.transacao.OrigemItemConsulta;
import com.joaovpg.economize.transacao.SituacaoTransacao;
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
            example = "\"2026-01\"",
            required = true)
        YearMonth inicio,
    @Schema(
            description = "Último mês consultado, inclusivo.",
            type = SchemaType.STRING,
            pattern = "\\d{4}-(0[1-9]|1[0-2])",
            example = "\"2026-03\"",
            required = true)
        YearMonth fim,
    @Schema(
            description =
                "Saldo imediatamente antes do primeiro dia do período, já considerando saldos"
                    + " iniciais e impactos anteriores dos filtros informados.",
            example = "1250.0000",
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
              example = "TRANSACAO_RECORRENTE",
              required = true)
          OrigemItemConsulta origem,
      @Schema(
              description =
                  "Identificador da operação. Em ocorrência recorrente ainda virtual, pode ser"
                      + " nulo; em transferência identifica a transferência, não cada lado.",
              nullable = true,
              required = false)
          UUID operacaoId,
      @Schema(
              description =
                  "Situação da transação; nula para SALDO_INICIAL_CONTA e ocorrência virtual.",
              nullable = true,
              required = false)
          SituacaoTransacao situacao,
      @Schema(description = "Descrição exibida no extrato.", example = "Aluguel", required = true)
          String descricao,
      @Schema(description = "Observação, quando houver.", nullable = true, required = false)
          String observacoes,
      @Schema(
              description =
                  "Valor com sinal de impacto na conta: receitas/entradas positivas,"
                      + " despesas/saídas negativas.",
              example = "-1800.0000",
              format = "double",
              required = true)
          BigDecimal valor,
      @Schema(
              description = "Data financeira que determina a ordenação do item.",
              example = "2026-02-05",
              format = "date",
              required = true)
          LocalDate dataFinanceira,
      @Schema(
              description =
                  "Instante da efetivação; nulo para itens planejados, virtuais ou saldo inicial.",
              nullable = true,
              format = "date-time",
              required = false)
          Instant efetivadoEm,
      @Schema(
              description = "Conta cujo saldo é impactado.",
              example = "00000000-0000-0000-0000-000000000001",
              required = true)
          UUID contaId,
      @Schema(
              description =
                  "Categoria do item, quando houver; saldo inicial e transferência não têm"
                      + " categoria.",
              nullable = true,
              required = false)
          UUID categoriaId,
      @Schema(
              description =
                  "Conta do outro lado da transferência; preenchido somente para TRANSFERENCIA.",
              nullable = true,
              required = false)
          UUID contaContraparteId,
      @Schema(
              description =
                  "Grupo da recorrência; preenchido somente para TRANSACAO_RECORRENTE ou PARCELA.",
              nullable = true,
              required = false)
          UUID grupoRecorrenciaId,
      @Schema(
              description =
                  "Segmento da recorrência; preenchido somente para TRANSACAO_RECORRENTE ou"
                      + " PARCELA.",
              nullable = true,
              required = false)
          UUID segmentoRecorrenciaId,
      @Schema(
              description =
                  "Data original da ocorrência recorrente, antes de eventual dataFinanceira"
                      + " efetiva.",
              nullable = true,
              format = "date",
              required = false)
          LocalDate dataOriginalRecorrencia,
      @Schema(
              description = "Número da parcela; preenchido somente para PARCELA.",
              nullable = true,
              required = false)
          Integer numeroParcela,
      @Schema(
              description = "Regra RFC 5545 da recorrência ou parcelamento.",
              nullable = true,
              example = "FREQ=MONTHLY;BYMONTHDAY=5",
              required = false)
          String rrule,
      @Schema(
              description = "Data de início do segmento recorrente.",
              nullable = true,
              format = "date",
              required = false)
          LocalDate inicioRecorrencia,
      @Schema(
              description = "Política aplicada ao cálculo da data de ocorrência.",
              nullable = true,
              example = "PADRAO",
              required = false)
          PoliticaDataOcorrencia politicaDataOcorrencia) {}
}
