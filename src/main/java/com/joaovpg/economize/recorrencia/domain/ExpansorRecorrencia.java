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
    if (deveAjustarDataDeParcelamento(regra, politicaData)) {
      return expandirComAjusteDeParcelamento(regra, inicio, fim);
    }
    return motor.expandir(regra, inicio, fim);
  }

  private boolean deveAjustarDataDeParcelamento(
      RegraRecorrencia regra, PoliticaDataOcorrencia politicaData) {
    return politicaData == PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
        && regra.diasMes().isEmpty()
        && (regra.frequencia() == FrequenciaRecorrencia.MONTHLY
            || regra.frequencia() == FrequenciaRecorrencia.YEARLY);
  }

  private List<OcorrenciaRecorrencia> expandirComAjusteDeParcelamento(
      RegraRecorrencia regra, LocalDate inicio, LocalDate fim) {
    return switch (regra.frequencia()) {
      case MONTHLY -> expandirMensalComAjuste(regra, inicio, fim);
      case YEARLY -> expandirAnualComAjuste(regra, inicio, fim);
      default -> motor.expandir(regra, inicio, fim);
    };
  }

  private List<OcorrenciaRecorrencia> expandirMensalComAjuste(
      RegraRecorrencia regra, LocalDate inicio, LocalDate fim) {
    var ocorrencias = new ArrayList<OcorrenciaRecorrencia>();
    var mes = YearMonth.from(regra.inicio());
    var numero = 0;
    while (!mes.atDay(1).isAfter(fim)) {
      var data = mes.atDay(Math.min(regra.inicio().getDayOfMonth(), mes.lengthOfMonth()));
      if (limiteAlcancado(regra, data, numero + 1)) {
        break;
      }
      numero++;
      adicionar(ocorrencias, data, numero, inicio, fim);
      mes = mes.plusMonths(regra.intervalo());
    }
    return ocorrencias;
  }

  private List<OcorrenciaRecorrencia> expandirAnualComAjuste(
      RegraRecorrencia regra, LocalDate inicio, LocalDate fim) {
    var ocorrencias = new ArrayList<OcorrenciaRecorrencia>();
    var ano = regra.inicio().getYear();
    var numero = 0;
    while (!LocalDate.of(ano, 1, 1).isAfter(fim)) {
      var mes = YearMonth.of(ano, regra.inicio().getMonthValue());
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
