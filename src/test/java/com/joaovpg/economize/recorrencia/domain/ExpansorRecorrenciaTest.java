package com.joaovpg.economize.recorrencia.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.joaovpg.economize.recorrencia.adapter.Ical4jMotorRecorrencia;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpansorRecorrenciaTest {
  private final ExpansorRecorrencia expansor =
      new ExpansorRecorrencia(new Ical4jMotorRecorrencia());

  @Test
  void geraRruleCanonicaComODiaDaSemanaDoInicio() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 5),
            FrequenciaRecorrencia.WEEKLY,
            2,
            false,
            null,
            LocalDate.of(2026, 2, 28));

    assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;UNTIL=20260228;WKST=MO", regra.rrule());
  }

  @Test
  void geraRruleDoDiaUtilComBySetPosDoInicio() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 11, 5), FrequenciaRecorrencia.MONTHLY, 2, true, null, null);

    assertEquals("FREQ=MONTHLY;INTERVAL=2;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=4", regra.rrule());
    assertEquals(4, regra.posicaoDiaUtil());
  }

  @Test
  void countIncluiODtstartComoPrimeiraOcorrencia() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 1), FrequenciaRecorrencia.DAILY, 1, false, 3, null);

    var ocorrencias = expansor.expandir(regra, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    assertEquals(
        List.of(
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 1), 1),
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 2), 2),
            new OcorrenciaRecorrencia(LocalDate.of(2026, 1, 3), 3)),
        ocorrencias);
  }

  @Test
  void parcelamentoMensalAjustaDataParaUltimoDiaDoMes() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 31), FrequenciaRecorrencia.MONTHLY, 1, false, 3, null);

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
  void diaUtilProjetaONesimoDiaUtilRespeitandoOIntervalo() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 11, 5), FrequenciaRecorrencia.MONTHLY, 2, true, null, null);

    var ocorrencias =
        expansor.expandir(
            regra, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 31), regra.politicaData());

    assertEquals(
        List.of(
            new OcorrenciaRecorrencia(LocalDate.of(2026, 11, 5), 1),
            new OcorrenciaRecorrencia(LocalDate.of(2027, 1, 6), 2),
            new OcorrenciaRecorrencia(LocalDate.of(2027, 3, 4), 3)),
        ocorrencias);
  }

  @Test
  void indiceDaOcorrenciaContinuaEstavelQuandoExpandeUmaJanelaPosterior() {
    var regra =
        new RegraRecorrencia(
            LocalDate.of(2026, 1, 10), FrequenciaRecorrencia.MONTHLY, 1, false, null, null);

    var ocorrencias = expansor.expandir(regra, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertEquals(List.of(new OcorrenciaRecorrencia(LocalDate.of(2026, 3, 10), 3)), ocorrencias);
  }

  @Test
  void rejeitaDiaUtilSemFrequenciaMensal() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegraRecorrencia(
                LocalDate.of(2026, 11, 5), FrequenciaRecorrencia.WEEKLY, 1, true, null, null));
  }

  @Test
  void rejeitaDiaUtilEmFimDeSemana() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegraRecorrencia(
                LocalDate.of(2026, 11, 28), FrequenciaRecorrencia.MONTHLY, 1, true, null, null));
  }

  @Test
  void aceitaDiaUtilNoVigesimoEMarcaRejeicaoNoVigesimoPrimeiro() {
    var vigesimo =
        new RegraRecorrencia(
            LocalDate.of(2026, 11, 27), FrequenciaRecorrencia.MONTHLY, 1, true, null, null);
    assertEquals(20, vigesimo.posicaoDiaUtil());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegraRecorrencia(
                LocalDate.of(2026, 11, 30), FrequenciaRecorrencia.MONTHLY, 1, true, null, null));
  }

  @Test
  void intervaloMaximoComAjusteNaoEstouraOLimiteDeDatas() {
    var anual =
        new RegraRecorrencia(
            LocalDate.of(999999990, 1, 10),
            FrequenciaRecorrencia.YEARLY,
            Integer.MAX_VALUE,
            false,
            null,
            null);
    var mensal =
        new RegraRecorrencia(
            LocalDate.of(999999990, 1, 31),
            FrequenciaRecorrencia.MONTHLY,
            Integer.MAX_VALUE,
            false,
            null,
            null);

    var datasAnuais =
        expansor.expandir(
            anual,
            LocalDate.of(999999990, 1, 1),
            LocalDate.MAX,
            PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES);
    var datasMensais =
        expansor.expandir(
            mensal,
            LocalDate.of(999999990, 1, 1),
            LocalDate.MAX,
            PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES);

    assertEquals(List.of(LocalDate.of(999999990, 1, 10)), datas(datasAnuais));
    assertEquals(List.of(LocalDate.of(999999990, 1, 31)), datas(datasMensais));
  }

  private static List<LocalDate> datas(List<OcorrenciaRecorrencia> ocorrencias) {
    return ocorrencias.stream().map(OcorrenciaRecorrencia::dataOriginal).toList();
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
                false,
                3,
                LocalDate.of(2026, 1, 31)));
  }
}
