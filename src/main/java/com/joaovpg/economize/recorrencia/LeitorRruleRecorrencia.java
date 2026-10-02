package com.joaovpg.economize.recorrencia;

import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
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

  public RegraRecorrencia ler(LocalDate inicio, String rrule) {
    return ler(inicio, rrule, true);
  }

  public RegraRecorrencia lerSegmento(LocalDate inicio, String rrule) {
    return ler(inicio, rrule, false);
  }

  private RegraRecorrencia ler(LocalDate inicio, String rrule, boolean exigirPrimeiraOcorrencia) {
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
    var diasMes = parseDiasMes(componentes.remove("BYMONTHDAY"));
    if (!componentes.isEmpty()) {
      throw new IllegalArgumentException("Componente RRULE nao suportado");
    }
    return exigirPrimeiraOcorrencia
        ? new RegraRecorrencia(inicio, frequencia, intervalo, diasSemana, diasMes, quantidade, ate)
        : RegraRecorrencia.paraSegmento(
            inicio, frequencia, intervalo, diasSemana, diasMes, quantidade, ate);
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

  private Set<Integer> parseDiasMes(String valor) {
    if (valor == null) {
      return Set.of();
    }
    var dias = new java.util.TreeSet<Integer>();
    Arrays.stream(valor.split(","))
        .forEach(
            dia -> {
              try {
                dias.add(Integer.valueOf(dia));
              } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("BYMONTHDAY invalido", exception);
              }
            });
    return Set.copyOf(dias);
  }
}
