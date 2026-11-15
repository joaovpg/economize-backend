package com.joaovpg.economize.recorrencia.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Regra de um segmento de recorrência. A data de início é sempre a primeira ocorrência e a âncora
 * da série: semanal repete no dia da semana do início, e mensal com dia útil repete no N-ésimo dia
 * útil do mês do início.
 */
public final class RegraRecorrencia {
  private static final DateTimeFormatter DATA_RRULE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int MAXIMO_DIA_UTIL = 20;

  private final LocalDate inicio;
  private final FrequenciaRecorrencia frequencia;
  private final int intervalo;
  private final Integer posicaoDiaUtil;
  private final Integer quantidadeOcorrencias;
  private final LocalDate ate;

  /**
   * @param diaUtil quando verdadeiro, a regra repete no N-ésimo dia útil do mês, com N calculado a
   *     partir de {@code inicio}
   */
  public RegraRecorrencia(
      LocalDate inicio,
      FrequenciaRecorrencia frequencia,
      int intervalo,
      boolean diaUtil,
      Integer quantidadeOcorrencias,
      LocalDate ate) {
    this.inicio = Objects.requireNonNull(inicio, "inicio");
    this.frequencia = Objects.requireNonNull(frequencia, "frequencia");
    this.intervalo = intervalo;
    this.quantidadeOcorrencias = quantidadeOcorrencias;
    this.ate = ate;
    this.posicaoDiaUtil = diaUtil ? validarDiaUtil(inicio, frequencia) : null;
    validarLimites();
  }

  /**
   * Posição de uma data entre os dias úteis (segunda a sexta) do seu mês, começando em 1. Um fim de
   * semana não tem posição, e esta operação devolve 0 nesse caso.
   */
  public static int posicaoNoMes(LocalDate data) {
    if (!ehDiaUtil(data)) {
      return 0;
    }
    var posicao = 0;
    for (var dia = 1; dia <= data.getDayOfMonth(); dia++) {
      if (ehDiaUtil(data.withDayOfMonth(dia))) {
        posicao++;
      }
    }
    return posicao;
  }

  public LocalDate inicio() {
    return inicio;
  }

  public FrequenciaRecorrencia frequencia() {
    return frequencia;
  }

  public int intervalo() {
    return intervalo;
  }

  public boolean diaUtil() {
    return posicaoDiaUtil != null;
  }

  /** N do dia útil, ou nulo quando a regra não é ancorada em dia útil. */
  public Integer posicaoDiaUtil() {
    return posicaoDiaUtil;
  }

  public Integer quantidadeOcorrencias() {
    return quantidadeOcorrencias;
  }

  public LocalDate ate() {
    return ate;
  }

  /**
   * Política de datas derivada da regra: o mensal e o anual ancorados no dia do mês usam o último
   * dia quando o mês é mais curto; os demais usam a regra padrão.
   */
  public PoliticaDataOcorrencia politicaData() {
    var mensalOuAnual =
        frequencia == FrequenciaRecorrencia.MONTHLY || frequencia == FrequenciaRecorrencia.YEARLY;
    return mensalOuAnual && !diaUtil()
        ? PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
        : PoliticaDataOcorrencia.PADRAO;
  }

  public String rrule() {
    var componentes = new ArrayList<String>();
    componentes.add("FREQ=" + frequencia.valorRrule());
    if (intervalo > 1) {
      componentes.add("INTERVAL=" + intervalo);
    }
    if (frequencia == FrequenciaRecorrencia.WEEKLY) {
      componentes.add("BYDAY=" + abreviacao(inicio.getDayOfWeek()));
    }
    if (diaUtil()) {
      componentes.add("BYDAY=MO,TU,WE,TH,FR");
      componentes.add("BYSETPOS=" + posicaoDiaUtil);
    }
    if (quantidadeOcorrencias != null) {
      componentes.add("COUNT=" + quantidadeOcorrencias);
    }
    if (ate != null) {
      componentes.add("UNTIL=" + DATA_RRULE.format(ate));
    }
    if (frequencia == FrequenciaRecorrencia.WEEKLY) {
      componentes.add("WKST=MO");
    }
    return String.join(";", componentes);
  }

  private void validarLimites() {
    if (intervalo < 1) {
      throw new IllegalArgumentException("O intervalo deve ser maior ou igual a um");
    }
    if (quantidadeOcorrencias != null && quantidadeOcorrencias < 1) {
      throw new IllegalArgumentException("A quantidade deve ser maior ou igual a um");
    }
    if (quantidadeOcorrencias != null && ate != null) {
      throw new IllegalArgumentException("COUNT e UNTIL nao podem coexistir");
    }
    if (ate != null && ate.isBefore(inicio)) {
      throw new IllegalArgumentException("UNTIL nao pode ser anterior ao inicio");
    }
  }

  private static int validarDiaUtil(LocalDate inicio, FrequenciaRecorrencia frequencia) {
    if (frequencia != FrequenciaRecorrencia.MONTHLY) {
      throw new IllegalArgumentException("O dia util exige frequencia mensal");
    }
    if (!ehDiaUtil(inicio)) {
      throw new IllegalArgumentException(
          "A data financeira do dia util deve ser de segunda a sexta");
    }
    var posicao = posicaoNoMes(inicio);
    if (posicao > MAXIMO_DIA_UTIL) {
      throw new IllegalArgumentException(
          "O dia util deve estar entre os 20 primeiros dias uteis do mes");
    }
    return posicao;
  }

  private static boolean ehDiaUtil(LocalDate data) {
    return data.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue();
  }

  private static String abreviacao(DayOfWeek dia) {
    return switch (dia) {
      case MONDAY -> "MO";
      case TUESDAY -> "TU";
      case WEDNESDAY -> "WE";
      case THURSDAY -> "TH";
      case FRIDAY -> "FR";
      case SATURDAY -> "SA";
      case SUNDAY -> "SU";
    };
  }
}
