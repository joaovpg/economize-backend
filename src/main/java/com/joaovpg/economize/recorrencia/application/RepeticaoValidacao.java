package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.recorrencia.domain.AncoragemRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RegraRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RepeticaoRecorrencia;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.shared.exception.ValidacaoException;
import java.time.LocalDate;

/**
 * Valida a configuração de uma repetição e a traduz para a regra do motor. A data financeira é
 * sempre a primeira ocorrência, então criação e edição usam a mesma validação.
 */
public final class RepeticaoValidacao {
  private RepeticaoValidacao() {}

  public static RegraRecorrencia validar(RepeticaoRecorrencia repeticao, LocalDate inicio) {
    var termino = repeticao.termino();
    LocalDate ate = null;
    Integer quantidade = null;
    switch (termino.tipo()) {
      case INDEFINIDO ->
          exigirAusentes(
              "Termino indefinido nao aceita campos",
              termino.data(),
              termino.quantidade(),
              termino.numeroPrimeiraParcela(),
              termino.totalParcelas());
      case EM_DATA -> {
        exigirAusentes(
            "Termino em data nao aceita quantidade nem parcelas",
            termino.quantidade(),
            termino.numeroPrimeiraParcela(),
            termino.totalParcelas());
        if (termino.data() == null) {
          throw new ValidacaoException("termino.data", "Data de termino obrigatoria");
        }
        if (termino.data().isBefore(inicio)) {
          throw repeticaoInvalida("O termino nao pode ser anterior ao inicio");
        }
        ate = termino.data();
      }
      case APOS_OCORRENCIAS -> {
        exigirAusentes(
            "Termino apos ocorrencias nao aceita data nem parcelas",
            termino.data(),
            termino.numeroPrimeiraParcela(),
            termino.totalParcelas());
        if (termino.quantidade() == null) {
          throw new ValidacaoException(
              "termino.quantidade", "Quantidade de ocorrencias obrigatoria");
        }
        if (termino.quantidade() < 1) {
          throw repeticaoInvalida("A quantidade de ocorrencias deve ser positiva");
        }
        quantidade = termino.quantidade();
      }
      case PARCELAS -> {
        exigirAusentes(
            "Termino de parcelas nao aceita data nem quantidade",
            termino.data(),
            termino.quantidade());
        if (termino.numeroPrimeiraParcela() == null) {
          throw new ValidacaoException(
              "termino.numeroPrimeiraParcela", "Numero da primeira parcela obrigatorio");
        }
        if (termino.totalParcelas() == null) {
          throw new ValidacaoException("termino.totalParcelas", "Total de parcelas obrigatorio");
        }
        validarNumeracao(termino.numeroPrimeiraParcela(), termino.totalParcelas());
        quantidade = termino.totalParcelas() - termino.numeroPrimeiraParcela() + 1;
      }
    }

    try {
      return new RegraRecorrencia(
          inicio,
          repeticao.frequencia(),
          repeticao.intervalo(),
          repeticao.ancoragem() == AncoragemRecorrencia.DIA_UTIL,
          quantidade,
          ate);
    } catch (IllegalArgumentException exception) {
      throw repeticaoInvalida(exception.getMessage());
    }
  }

  private static void validarNumeracao(int numeroPrimeiraParcela, int totalParcelas) {
    if (numeroPrimeiraParcela < 1 || totalParcelas < 1 || numeroPrimeiraParcela > totalParcelas) {
      throw new RegraNegocioException(
          "PARCELAMENTO_INVALIDO",
          "A numeracao deve ser positiva e a primeira parcela nao pode exceder o total");
    }
  }

  private static void exigirAusentes(String mensagem, Object... valores) {
    for (var valor : valores) {
      if (valor != null) {
        throw repeticaoInvalida(mensagem);
      }
    }
  }

  private static RegraNegocioException repeticaoInvalida(String mensagem) {
    return new RegraNegocioException("REPETICAO_INVALIDA", mensagem);
  }
}
