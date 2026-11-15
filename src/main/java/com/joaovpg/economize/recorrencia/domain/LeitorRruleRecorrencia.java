package com.joaovpg.economize.recorrencia.domain;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

@ApplicationScoped
public class LeitorRruleRecorrencia {
  private static final DateTimeFormatter DATA_RRULE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final Set<DayOfWeek> DIAS_UTEIS =
      EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

  /**
   * Lê a RRULE de um segmento cuja data de início é a primeira ocorrência. Aceita somente as formas
   * geradas por RegraRecorrencia.rrule().
   */
  public RegraRecorrencia ler(LocalDate inicio, String rrule) {
    if (inicio == null || rrule == null || rrule.isBlank() || rrule.startsWith("RRULE:")) {
      throw new IllegalArgumentException("RRULE invalida");
    }
    var componentes = new HashMap<String, String>();
    for (var componente : rrule.split(";")) {
      var partes = componente.split("=", 2);
      if (partes.length != 2 || partes[0].isBlank() || partes[1].isBlank()) {
        throw new IllegalArgumentException("RRULE invalida");
      }
      if (componentes.put(partes[0], partes[1]) != null) {
        throw new IllegalArgumentException("RRULE duplicada");
      }
    }
    var frequencia = parseFrequencia(componentes.remove("FREQ"));
    var intervalo = parseInteiro(componentes.remove("INTERVAL"));
    var quantidade = parseInteiroOpcional(componentes.remove("COUNT"), "COUNT");
    var ate = parseData(componentes.remove("UNTIL"));
    componentes.remove("WKST");
    var diasSemana = parseDiasSemana(componentes.remove("BYDAY"));
    var posicao = parseInteiroOpcional(componentes.remove("BYSETPOS"), "BYSETPOS");
    if (!componentes.isEmpty()) {
      throw new IllegalArgumentException("Componente RRULE nao suportado");
    }
    var diaUtil = posicao != null;
    validarDias(inicio, frequencia, diasSemana, diaUtil, posicao);
    return new RegraRecorrencia(inicio, frequencia, intervalo, diaUtil, quantidade, ate);
  }

  private static void validarDias(
      LocalDate inicio,
      FrequenciaRecorrencia frequencia,
      Set<DayOfWeek> diasSemana,
      boolean diaUtil,
      Integer posicao) {
    if (frequencia == FrequenciaRecorrencia.WEEKLY) {
      // Sem BYDAY, a RFC 5545 usa o dia da semana do DTSTART.
      if (!diasSemana.isEmpty() && !diasSemana.equals(Set.of(inicio.getDayOfWeek()))) {
        throw new IllegalArgumentException("BYDAY deve ser o dia da semana do inicio");
      }
    } else if (diaUtil) {
      if (!diasSemana.equals(DIAS_UTEIS)) {
        throw new IllegalArgumentException("BYDAY do dia util deve ser de segunda a sexta");
      }
      if (posicao != RegraRecorrencia.posicaoNoMes(inicio)) {
        throw new IllegalArgumentException("BYSETPOS diferente da posicao do inicio");
      }
    } else if (!diasSemana.isEmpty()) {
      throw new IllegalArgumentException("BYDAY nao e valido para esta frequencia");
    }
  }

  private FrequenciaRecorrencia parseFrequencia(String valor) {
    if (valor == null) {
      throw new IllegalArgumentException("FREQ obrigatorio");
    }
    try {
      return FrequenciaRecorrencia.valueOf(valor);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("FREQ invalido", exception);
    }
  }

  private int parseInteiro(String valor) {
    var resultado = parseInteiroOpcional(valor, "INTERVAL");
    return resultado == null ? 1 : resultado;
  }

  private Integer parseInteiroOpcional(String valor, String nome) {
    if (valor == null) {
      return null;
    }
    try {
      int numero = Integer.parseInt(valor);
      if (numero < 1) {
        throw new IllegalArgumentException(nome + " invalido");
      }
      return numero;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(nome + " invalido", exception);
    }
  }

  private LocalDate parseData(String valor) {
    if (valor == null) {
      return null;
    }
    try {
      if (valor.length() != 8) {
        throw new IllegalArgumentException("UNTIL invalido");
      }
      return LocalDate.parse(valor, DATA_RRULE);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("UNTIL invalido", exception);
    }
  }

  private Set<DayOfWeek> parseDiasSemana(String valor) {
    if (valor == null) {
      return Set.of();
    }
    var dias = EnumSet.noneOf(DayOfWeek.class);
    Arrays.stream(valor.split(",")).forEach(dia -> dias.add(parseDiaSemana(dia)));
    return Set.copyOf(dias);
  }

  private DayOfWeek parseDiaSemana(String valor) {
    return switch (valor) {
      case "MO" -> DayOfWeek.MONDAY;
      case "TU" -> DayOfWeek.TUESDAY;
      case "WE" -> DayOfWeek.WEDNESDAY;
      case "TH" -> DayOfWeek.THURSDAY;
      case "FR" -> DayOfWeek.FRIDAY;
      case "SA" -> DayOfWeek.SATURDAY;
      case "SU" -> DayOfWeek.SUNDAY;
      default -> throw new IllegalArgumentException("BYDAY invalido");
    };
  }
}
