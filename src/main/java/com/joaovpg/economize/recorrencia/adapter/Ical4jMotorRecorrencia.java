package com.joaovpg.economize.recorrencia.adapter;

import com.joaovpg.economize.recorrencia.domain.MotorRecorrencia;
import com.joaovpg.economize.recorrencia.domain.OcorrenciaRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RegraRecorrencia;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.fortuna.ical4j.model.Recur;

@ApplicationScoped
public class Ical4jMotorRecorrencia implements MotorRecorrencia {
  @Override
  public List<OcorrenciaRecorrencia> expandir(
      RegraRecorrencia regra, LocalDate inicio, LocalDate fim) {
    if (inicio == null || fim == null || inicio.isAfter(fim)) {
      return List.of();
    }

    var recorrencia = new Recur<LocalDate>(regra.rrule());
    var datas = recorrencia.getDates(regra.inicio(), regra.inicio(), fim);
    var ocorrencias = new ArrayList<OcorrenciaRecorrencia>();
    var numero = 0;

    for (var data : datas) {
      if (data.isBefore(regra.inicio())) {
        continue;
      }
      numero++;
      if (!data.isBefore(inicio) && !data.isAfter(fim)) {
        ocorrencias.add(new OcorrenciaRecorrencia(data, numero));
      }
    }

    return ocorrencias;
  }
}
