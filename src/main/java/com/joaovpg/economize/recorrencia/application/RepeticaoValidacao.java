package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.recorrencia.RegraRecorrencia;
import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.shared.exception.ValidacaoException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Valida a configuração estruturada de uma repetição ou parcelamento e a traduz para a regra usada
 * pelo motor. As combinações aceitas seguem a especificação de contratos unificados.
 */
public final class RepeticaoValidacao {
  private RepeticaoValidacao() {}

  public static RegraRecorrencia validar(RepeticaoRecorrencia repeticao, LocalDate inicio) {
    var parcelamento = repeticao.parcelamento();
    var termino = repeticao.termino();
    if (parcelamento != null) {
      if (termino != null) {
        throw new RegraNegocioException(
            "PARCELAMENTO_INVALIDO", "Parcelamento nao aceita termino da repeticao");
      }
      validarNumeracao(parcelamento);
    } else if (termino == null) {
      throw new ValidacaoException("termino", "Termino obrigatorio quando nao ha parcelamento");
    }
    if (repeticao.intervalo() < 1) {
      throw repeticaoInvalida("O intervalo deve ser maior ou igual a 1");
    }

    var diasSemana = repeticao.diasSemana();
    var diasMes = repeticao.diasMes();
    validarDias(diasSemana, diasMes);
    validarFrequencia(
        repeticao.frequencia(),
        repeticao.politicaData(),
        parcelamento != null,
        inicio,
        diasSemana,
        diasMes);

    LocalDate ate = null;
    Integer quantidade = null;
    if (parcelamento != null) {
      quantidade = parcelamento.totalParcelas() - parcelamento.numeroPrimeiraParcela() + 1;
    } else {
      switch (termino.tipo()) {
        case INDEFINIDO -> {
          if (termino.data() != null || termino.quantidade() != null) {
            throw repeticaoInvalida("Termino indefinido nao aceita data nem quantidade");
          }
        }
        case EM_DATA -> {
          if (termino.quantidade() != null) {
            throw repeticaoInvalida("Termino em data nao aceita quantidade");
          }
          if (termino.data() == null) {
            throw new ValidacaoException("termino.data", "Data de termino obrigatoria");
          }
          if (termino.data().isBefore(inicio)) {
            throw repeticaoInvalida("O termino nao pode ser anterior ao inicio");
          }
          ate = termino.data();
        }
        case APOS_OCORRENCIAS -> {
          if (termino.data() != null) {
            throw repeticaoInvalida("Termino apos ocorrencias nao aceita data");
          }
          if (termino.quantidade() == null) {
            throw new ValidacaoException(
                "termino.quantidade", "Quantidade de ocorrencias obrigatoria");
          }
          if (termino.quantidade() < 1) {
            throw repeticaoInvalida("A quantidade de ocorrencias deve ser positiva");
          }
          quantidade = termino.quantidade();
        }
      }
    }

    try {
      return new RegraRecorrencia(
          inicio,
          repeticao.frequencia(),
          repeticao.intervalo(),
          Set.copyOf(diasSemana),
          Set.copyOf(diasMes),
          quantidade,
          ate);
    } catch (IllegalArgumentException exception) {
      throw repeticaoInvalida(exception.getMessage());
    }
  }

  private static void validarNumeracao(RepeticaoRecorrencia.Parcelamento parcelamento) {
    if (parcelamento.numeroPrimeiraParcela() < 1
        || parcelamento.totalParcelas() < 1
        || parcelamento.numeroPrimeiraParcela() > parcelamento.totalParcelas()) {
      throw new RegraNegocioException(
          "PARCELAMENTO_INVALIDO",
          "A numeracao deve ser positiva e a primeira parcela nao pode exceder o total");
    }
  }

  private static void validarDias(List<DayOfWeek> diasSemana, List<Integer> diasMes) {
    if (diasSemana.stream().anyMatch(Objects::isNull)
        || diasMes.stream().anyMatch(Objects::isNull)) {
      throw repeticaoInvalida("Os dias informados devem ser valores validos");
    }
    if (new HashSet<>(diasSemana).size() != diasSemana.size()
        || new HashSet<>(diasMes).size() != diasMes.size()) {
      throw repeticaoInvalida("Os dias nao podem se repetir");
    }
    if (diasMes.stream().anyMatch(dia -> dia < 1 || dia > 31)) {
      throw repeticaoInvalida("Os dias do mes devem estar entre 1 e 31");
    }
  }

  private static void validarFrequencia(
      FrequenciaRecorrencia frequencia,
      PoliticaDataOcorrencia politica,
      boolean parcelado,
      LocalDate inicio,
      List<DayOfWeek> diasSemana,
      List<Integer> diasMes) {
    var semDias = diasSemana.isEmpty() && diasMes.isEmpty();
    if (politica == PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
        && !(semDias
            && (frequencia == FrequenciaRecorrencia.MONTHLY
                || frequencia == FrequenciaRecorrencia.YEARLY))) {
      throw repeticaoInvalida(
          "O ajuste do ultimo dia do mes exige frequencia mensal ou anual sem dias informados");
    }
    switch (frequencia) {
      case DAILY, YEARLY -> exigir(semDias, "A frequencia nao aceita dias informados");
      case WEEKLY -> {
        exigir(diasMes.isEmpty(), "A repeticao semanal nao aceita dias do mes");
        exigir(!diasSemana.isEmpty(), "A repeticao semanal exige dias da semana");
        if (parcelado) {
          exigir(
              diasSemana.size() == 1 && diasSemana.getFirst() == inicio.getDayOfWeek(),
              "O parcelamento semanal usa somente o dia da semana da data financeira");
        }
      }
      case MONTHLY -> {
        exigir(diasSemana.isEmpty(), "A repeticao mensal nao aceita dias da semana");
        if (parcelado) {
          exigir(diasMes.isEmpty(), "O parcelamento mensal nao aceita dias do mes");
        } else if (politica == PoliticaDataOcorrencia.PADRAO) {
          exigir(!diasMes.isEmpty(), "A repeticao mensal sem parcelamento exige dias do mes");
        }
      }
    }
  }

  private static void exigir(boolean condicao, String mensagem) {
    if (!condicao) {
      throw repeticaoInvalida(mensagem);
    }
  }

  private static RegraNegocioException repeticaoInvalida(String mensagem) {
    return new RegraNegocioException("REPETICAO_INVALIDA", mensagem);
  }
}
