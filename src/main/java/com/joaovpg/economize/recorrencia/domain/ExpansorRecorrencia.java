package com.joaovpg.economize.recorrencia.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ExpansorRecorrencia {
  private final MotorRecorrencia motor;

  @Inject
  public ExpansorRecorrencia(MotorRecorrencia motor) {
    this.motor = motor;
  }

  public List<OcorrenciaRecorrencia> expandir(
      RegraRecorrencia regra, LocalDate inicio, LocalDate fim) {
    return expandir(regra, inicio, fim, PoliticaDataOcorrencia.PADRAO);
  }

  public List<OcorrenciaRecorrencia> expandir(
      RegraRecorrencia regra,
      LocalDate inicio,
      LocalDate fim,
      PoliticaDataOcorrencia politicaData) {
    if (inicio == null || fim == null || inicio.isAfter(fim)) {
      return List.of();
    }
    if (deveAjustarUltimoDiaDoMes(regra, politicaData)) {
      return expandirComAjusteDoUltimoDia(regra, inicio, fim);
    }
    return motor.expandir(regra, inicio, fim);
  }

  private boolean deveAjustarUltimoDiaDoMes(
      RegraRecorrencia regra, PoliticaDataOcorrencia politicaData) {
    return politicaData == PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
        && (regra.frequencia() == FrequenciaRecorrencia.MONTHLY
            || regra.frequencia() == FrequenciaRecorrencia.YEARLY);
  }

  private List<OcorrenciaRecorrencia> expandirComAjusteDoUltimoDia(
      RegraRecorrencia regra, LocalDate inicio, LocalDate fim) {
    return switch (regra.frequencia()) {
      case MONTHLY -> expandirMensalComAjuste(regra, inicio, fim);
      case YEARLY -> expandirAnualComAjuste(regra, inicio, fim);
      default -> motor.expandir(regra, inicio, fim);
    };
  }

  /**
   * Itera os meses em aritmética de long: um intervalo grande não estoura e a repetição para ao
   * passar do mês de {@code fim}, sem chegar a um mês fora do intervalo de datas do LocalDate.
   */
  private List<OcorrenciaRecorrencia> expandirMensalComAjuste(
      RegraRecorrencia regra, LocalDate inicio, LocalDate fim) {
    var ocorrencias = new ArrayList<OcorrenciaRecorrencia>();
    var indiceMes = indiceMes(regra.inicio());
    var ultimoIndice = indiceMes(fim);
    var numero = 0;
    while (indiceMes <= ultimoIndice) {
      var mes = mesDoIndice(indiceMes);
      var data = mes.atDay(Math.min(regra.inicio().getDayOfMonth(), mes.lengthOfMonth()));
      if (limiteAlcancado(regra, data, numero + 1)) {
        break;
      }
      numero++;
      adicionar(ocorrencias, data, numero, inicio, fim);
      indiceMes += regra.intervalo();
    }
    return ocorrencias;
  }

  private List<OcorrenciaRecorrencia> expandirAnualComAjuste(
      RegraRecorrencia regra, LocalDate inicio, LocalDate fim) {
    var ocorrencias = new ArrayList<OcorrenciaRecorrencia>();
    long ano = regra.inicio().getYear();
    var numero = 0;
    while (ano <= fim.getYear()) {
      var mes = YearMonth.of(Math.toIntExact(ano), regra.inicio().getMonthValue());
      var data = mes.atDay(Math.min(regra.inicio().getDayOfMonth(), mes.lengthOfMonth()));
      if (limiteAlcancado(regra, data, numero + 1)) {
        break;
      }
      numero++;
      adicionar(ocorrencias, data, numero, inicio, fim);
      ano += regra.intervalo();
    }
    return ocorrencias;
  }

  private static long indiceMes(LocalDate data) {
    return data.getYear() * 12L + data.getMonthValue() - 1;
  }

  private static YearMonth mesDoIndice(long indice) {
    return YearMonth.of(
        Math.toIntExact(Math.floorDiv(indice, 12L)),
        Math.toIntExact(Math.floorMod(indice, 12L)) + 1);
  }

  private boolean limiteAlcancado(RegraRecorrencia regra, LocalDate data, int numero) {
    return (regra.ate() != null && data.isAfter(regra.ate()))
        || (regra.quantidadeOcorrencias() != null && numero > regra.quantidadeOcorrencias());
  }

  private void adicionar(
      List<OcorrenciaRecorrencia> ocorrencias,
      LocalDate data,
      int numero,
      LocalDate inicio,
      LocalDate fim) {
    if (!data.isBefore(inicio) && !data.isAfter(fim)) {
      ocorrencias.add(new OcorrenciaRecorrencia(data, numero));
    }
  }
}
