package com.joaovpg.economize.recorrencia.domain;

import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import java.time.LocalDate;

/**
 * Configuração de repetição de um segmento, traduzida da RRULE sem recalcular datas. A mesma forma
 * é usada na entrada de criação e de edição, exceto pelo número do dia útil, que o servidor calcula
 * e devolve na leitura.
 *
 * @param frequencia frequência da repetição
 * @param intervalo intervalo entre ciclos da frequência
 * @param ancoragem âncora da repetição dentro do mês ou do ano
 * @param numeroDiaUtil N do dia útil; nulo quando a ancoragem é DIA_DO_MES
 * @param termino término da repetição, inclusive a numeração de um Parcelamento
 */
public record RepeticaoRecorrencia(
    FrequenciaRecorrencia frequencia,
    int intervalo,
    AncoragemRecorrencia ancoragem,
    Integer numeroDiaUtil,
    Termino termino) {

  /**
   * Término da repetição. Cada tipo usa somente os próprios campos: data em EM_DATA, quantidade em
   * APOS_OCORRENCIAS e numeração em PARCELAS.
   */
  public record Termino(
      TipoTermino tipo,
      LocalDate data,
      Integer quantidade,
      Integer numeroPrimeiraParcela,
      Integer totalParcelas) {}

  /** Indica se a repetição é um Parcelamento, ou seja, tem numeração de parcelas. */
  public boolean parcelado() {
    return termino.tipo() == TipoTermino.PARCELAS;
  }

  /**
   * Forma de entrada desta repetição, sem o número do dia útil calculado pelo servidor. Serve para
   * comparar a configuração recebida com a lida.
   */
  public RepeticaoRecorrencia semNumeroDiaUtil() {
    return new RepeticaoRecorrencia(frequencia, intervalo, ancoragem, null, termino);
  }

  public static RepeticaoRecorrencia de(SegmentoRecorrencia segmento, RegraRecorrencia regra) {
    var termino =
        segmento.getNumeroPrimeiraParcela() != null
            ? new Termino(
                TipoTermino.PARCELAS,
                null,
                null,
                segmento.getNumeroPrimeiraParcela(),
                segmento.getQuantidadeTotalOriginal())
            : termino(regra, segmento.getFim());
    return new RepeticaoRecorrencia(
        regra.frequencia(),
        regra.intervalo(),
        regra.diaUtil() ? AncoragemRecorrencia.DIA_UTIL : AncoragemRecorrencia.DIA_DO_MES,
        regra.posicaoDiaUtil(),
        termino);
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
      return new Termino(TipoTermino.EM_DATA, data, null, null, null);
    }
    if (regra.quantidadeOcorrencias() != null) {
      return new Termino(
          TipoTermino.APOS_OCORRENCIAS, null, regra.quantidadeOcorrencias(), null, null);
    }
    return new Termino(TipoTermino.INDEFINIDO, null, null, null, null);
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
