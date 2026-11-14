package com.joaovpg.economize.recorrencia.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.joaovpg.economize.recorrencia.adapter.Ical4jMotorRecorrencia;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExpansorRecorrenciaTest {
  private final ExpansorRecorrencia expansor =
      new ExpansorRecorrencia(new Ical4jMotorRecorrencia());

  @Test
  void geraRruleCanonicaComComponentesOrdenados() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 5),
            FrequenciaRecorrencia.WEEKLY,
            2,
            Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY),
            Set.of(),
            null,
            LocalDate.of(2026, 2, 28));

    assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20260228;WKST=MO", regra.rrule());
  }

  @Test
  void countIncluiODtstartComoPrimeiraOcorrencia() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 1), FrequenciaRecorrencia.DAILY, 1, Set.of(), Set.of(), 3, null);

    var ocorrencias = expansor.expandir(regra, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    assertEquals(
        List.of(
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 1), 1),
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 2), 2),
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 3), 3)),
        ocorrencias);
  }

  @Test
  void recorrenciaMensalOmiteDataInexistente() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 31),
            FrequenciaRecorrencia.MONTHLY,
            1,
            Set.of(),
            Set.of(31),
            null,
            LocalDate.of(2026, 4, 30));

    var ocorrencias = expansor.expandir(regra, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

    assertEquals(
        List.of(
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 31), 1),
            new OcorrenciaRecorrencia(LocalDate.of(2026, 3, 31), 2)),
        ocorrencias);
  }

  @Test
  void recorrenciaMensalAncoradaOmiteMesSemDataNaPoliticaPadrao() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 31),
            FrequenciaRecorrencia.MONTHLY,
            1,
            Set.of(),
            Set.of(),
            null,
            LocalDate.of(2026, 4, 30));

    var ocorrencias = expansor.expandir(regra, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30));

    assertEquals(
        List.of(
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 31), 1),
            new OcorrenciaRecorrencia(LocalDate.of(2026, 3, 31), 2)),
        ocorrencias);
  }

  @Test
  void parcelamentoMensalAjustaDataParaUltimoDiaDoMes() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 31),
            FrequenciaRecorrencia.MONTHLY,
            1,
            Set.of(),
            Set.of(),
            3,
            null);

    var ocorrencias =
        expansor.expandir(
            regra,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31),
            PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES);

    assertEquals(
        List.of(
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 31), 1),
            new OcorrenciaRecorrencia(LocalDate.of(2026, 2, 28), 2),
            new OcorrenciaRecorrencia(LocalDate.of(2026, 3, 31), 3)),
        ocorrencias);
  }

  @Test
  void indiceDaOcorrenciaContinuaEstavelQuandoExpandeUmaJanelaPosterior() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 10),
            FrequenciaRecorrencia.MONTHLY,
            1,
            Set.of(),
            Set.of(10),
            null,
            null);

    var ocorrencias = expansor.expandir(regra, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertEquals(List.of(new OcorrenciaRecorrencia(LocalDate.of(2026, 3, 10), 3)), ocorrencias);
  }

  @Test
  void rejeitaRegraComCountEUntilAoMesmoTempo() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegraRecorrencia(
                LocalDate.of(2026, 1, 1),
                FrequenciaRecorrencia.DAILY,
                1,
                Set.of(),
                Set.of(),
                3,
                LocalDate.of(2026, 1, 31)));
  }
}
