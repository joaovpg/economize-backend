package com.joaovpg.economize.recorrencia.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class RegraRecorrencia {
  private static final DateTimeFormatter DATA_RRULE = DateTimeFormatter.BASIC_ISO_DATE;

  private final LocalDate inicio;
  private final FrequenciaRecorrencia frequencia;
  private final int intervalo;
  private final Set<DayOfWeek> diasSemana;
  private final Set<Integer> diasMes;
  private final Integer quantidadeOcorrencias;
  private final LocalDate ate;

  public RegraRecorrencia(
      LocalDate inicio,
      FrequenciaRecorrencia frequencia,
      int intervalo,
      Set<DayOfWeek> diasSemana,
      Set<Integer> diasMes,
      Integer quantidadeOcorrencias,
      LocalDate ate) {
    this(inicio, frequencia, intervalo, diasSemana, diasMes, quantidadeOcorrencias, ate, true);
  }

  private RegraRecorrencia(
      LocalDate inicio,
      FrequenciaRecorrencia frequencia,
      int intervalo,
      Set<DayOfWeek> diasSemana,
      Set<Integer> diasMes,
      Integer quantidadeOcorrencias,
      LocalDate ate,
      boolean exigirPrimeiraOcorrencia) {
    this.inicio = Objects.requireNonNull(inicio, "inicio");
    this.frequencia = Objects.requireNonNull(frequencia, "frequencia");
    this.intervalo = intervalo;
    this.diasSemana = diasSemana == null ? Set.of() : Set.copyOf(diasSemana);
    this.diasMes = diasMes == null ? Set.of() : Set.copyOf(diasMes);
    this.quantidadeOcorrencias = quantidadeOcorrencias;
    this.ate = ate;
    validarLimites();
    if (exigirPrimeiraOcorrencia) {
      validarPadraoInicial();
    } else {
      validarPadraoDoSegmento();
    }
  }

  public static RegraRecorrencia paraSegmento(
      LocalDate inicio,
      FrequenciaRecorrencia frequencia,
      int intervalo,
      Set<DayOfWeek> diasSemana,
      Set<Integer> diasMes,
      Integer quantidadeOcorrencias,
      LocalDate ate) {
    return new RegraRecorrencia(
        inicio, frequencia, intervalo, diasSemana, diasMes, quantidadeOcorrencias, ate, false);
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

  public Set<DayOfWeek> diasSemana() {
    return diasSemana;
  }

  public Set<Integer> diasMes() {
    return diasMes;
  }

  public Integer quantidadeOcorrencias() {
    return quantidadeOcorrencias;
  }

  public LocalDate ate() {
    return ate;
  }

  public String rrule() {
    var componentes = new ArrayList<String>();
    componentes.add("FREQ=" + frequencia.valorRrule());
    if (intervalo > 1) {
      componentes.add("INTERVAL=" + intervalo);
    }
    if (!diasSemana.isEmpty()) {
      componentes.add(
          "BYDAY="
              + diasSemana.stream()
                  .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                  .map(RegraRecorrencia::abreviacaoDia)
                  .collect(Collectors.joining(",")));
    }
    if (!diasMes.isEmpty()) {
      componentes.add(
          "BYMONTHDAY="
              + new TreeSet<>(diasMes)
                  .stream().map(String::valueOf).collect(Collectors.joining(",")));
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

  public boolean finita() {
    return quantidadeOcorrencias != null || ate != null;
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
    if (diasMes.stream().anyMatch(dia -> dia < 1 || dia > 31)) {
      throw new IllegalArgumentException("Os dias do mes devem estar entre 1 e 31");
    }
  }

  private void validarPadraoInicial() {
    switch (frequencia) {
      case DAILY -> {
        exigirVazio(diasSemana, "BYDAY nao e valido para recorrencia diaria");
        exigirVazio(diasMes, "BYMONTHDAY nao e valido para recorrencia diaria");
      }
      case WEEKLY -> {
        validarPadraoSemanal();
        var deslocamentoInicio = inicio.getDayOfWeek().getValue();
        if (diasSemana.stream().anyMatch(dia -> dia.getValue() < deslocamentoInicio)) {
          throw new IllegalArgumentException("O inicio deve ser a primeira ocorrencia da regra");
        }
      }
      case MONTHLY -> {
        validarPadraoMensal();
        if (!diasMes.isEmpty() && diasMes.stream().anyMatch(dia -> dia < inicio.getDayOfMonth())) {
          throw new IllegalArgumentException("O inicio deve ser a primeira ocorrencia da regra");
        }
      }
      case YEARLY -> {
        exigirVazio(diasSemana, "BYDAY nao e valido para recorrencia anual");
        exigirVazio(diasMes, "BYMONTHDAY nao e configurado para recorrencia anual");
      }
    }
  }

  private void validarPadraoDoSegmento() {
    switch (frequencia) {
      case DAILY -> {
        exigirVazio(diasSemana, "BYDAY nao e valido para recorrencia diaria");
        exigirVazio(diasMes, "BYMONTHDAY nao e valido para recorrencia diaria");
      }
      case WEEKLY -> validarPadraoSemanal();
      case MONTHLY -> validarPadraoMensal();
      case YEARLY -> {
        exigirVazio(diasSemana, "BYDAY nao e valido para recorrencia anual");
        exigirVazio(diasMes, "BYMONTHDAY nao e configurado para recorrencia anual");
      }
    }
  }

  private void validarPadraoSemanal() {
    if (diasSemana.isEmpty()) {
      throw new IllegalArgumentException("A recorrencia semanal exige dias da semana");
    }
    exigirVazio(diasMes, "BYMONTHDAY nao e valido para recorrencia semanal");
    if (!diasSemana.contains(inicio.getDayOfWeek())) {
      throw new IllegalArgumentException("O inicio deve ser uma ocorrencia da regra");
    }
  }

  private void validarPadraoMensal() {
    exigirVazio(diasSemana, "BYDAY nao e valido para recorrencia mensal");
    if (!diasMes.isEmpty() && !diasMes.contains(inicio.getDayOfMonth())) {
      throw new IllegalArgumentException("O inicio deve ser uma ocorrencia da regra");
    }
  }

  private void exigirVazio(Set<?> valores, String mensagem) {
    if (!valores.isEmpty()) {
      throw new IllegalArgumentException(mensagem);
    }
  }

  private static String abreviacaoDia(DayOfWeek dia) {
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
