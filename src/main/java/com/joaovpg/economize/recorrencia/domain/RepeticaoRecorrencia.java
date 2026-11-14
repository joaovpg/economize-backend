package com.joaovpg.economize.recorrencia.domain;

import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Configuração estruturada de um segmento, traduzida da RRULE sem recalcular datas.
 *
 * @param frequencia frequência da regra
 * @param intervalo intervalo entre ciclos da frequência
 * @param diasSemana dias da semana ordenados de segunda a domingo; vazio quando inaplicável
 * @param diasMes dias do mês em ordem crescente; vazio quando inaplicável
 * @param politicaData política de datas do segmento
 * @param termino término da repetição; nulo quando há parcelamento
 * @param parcelamento numeração das parcelas; nulo em repetições sem parcelas
 */
public record RepeticaoRecorrencia(
    FrequenciaRecorrencia frequencia,
    int intervalo,
    List<DayOfWeek> diasSemana,
    List<Integer> diasMes,
    PoliticaDataOcorrencia politicaData,
    Termino termino,
    Parcelamento parcelamento) {

  public record Termino(TipoTermino tipo, LocalDate data, Integer quantidade) {}

  public record Parcelamento(int numeroPrimeiraParcela, int totalParcelas) {}

  public static RepeticaoRecorrencia de(SegmentoRecorrencia segmento, RegraRecorrencia regra) {
    var parcelado = segmento.getNumeroPrimeiraParcela() != null;
    return new RepeticaoRecorrencia(
        regra.frequencia(),
        regra.intervalo(),
        regra.diasSemana().stream().sorted().toList(),
        regra.diasMes().stream().sorted().toList(),
        segmento.getPoliticaData(),
        parcelado ? null : termino(regra, segmento.getFim()),
        parcelado
            ? new Parcelamento(
                segmento.getNumeroPrimeiraParcela(), segmento.getQuantidadeTotalOriginal())
            : null);
  }

  /**
   * O término efetivo é o que vier primeiro entre o UNTIL da regra e o fim do segmento, que
   * registra cortes de série. Sem data, a quantidade da regra (COUNT) é usada. Um segmento cortado
   * de regra com COUNT tem fim sempre anterior ao último COUNT, então a data representa o mesmo
   * conjunto de ocorrências.
   */
  private static Termino termino(RegraRecorrencia regra, LocalDate fimSegmento) {
    var data = menor(regra.ate(), fimSegmento);
    if (data != null) {
      return new Termino(TipoTermino.EM_DATA, data, null);
    }
    if (regra.quantidadeOcorrencias() != null) {
      return new Termino(TipoTermino.APOS_OCORRENCIAS, null, regra.quantidadeOcorrencias());
    }
    return new Termino(TipoTermino.INDEFINIDO, null, null);
  }

  private static LocalDate menor(LocalDate primeira, LocalDate segunda) {
    if (primeira == null) {
      return segunda;
    }
    if (segunda == null) {
      return primeira;
    }
    return primeira.isBefore(segunda) ? primeira : segunda;
  }
}
