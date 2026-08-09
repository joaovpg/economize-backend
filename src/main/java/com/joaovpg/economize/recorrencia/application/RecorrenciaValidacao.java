package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.RegraRecorrencia;
import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

final class RecorrenciaValidacao {
  private RecorrenciaValidacao() {}

  static Usuario usuario(UsuarioRepository repository, UUID usuarioId) {
    return repository.buscarAtivo(usuarioId).orElseThrow(() -> naoEncontrado("Usuario"));
  }

  static ContaFinanceira conta(
      ContaFinanceiraRepository repository, UUID contaId, UUID usuarioId, LocalDate inicio) {
    var conta =
        repository
            .buscarAtivaDoUsuario(contaId, usuarioId)
            .orElseThrow(() -> naoEncontrado("Conta"));
    if (inicio.isBefore(conta.getDataSaldoInicial())) {
      throw new RegraNegocioException(
          "DATA_INICIAL_ANTERIOR_SALDO_INICIAL",
          "A data inicial nao pode ser anterior a data do saldo inicial da conta");
    }
    return conta;
  }

  static Categoria categoria(CategoriaRepository repository, UUID categoriaId, UUID usuarioId) {
    return categoriaId == null
        ? null
        : repository
            .buscarAtivaDoUsuario(categoriaId, usuarioId)
            .orElseThrow(() -> naoEncontrado("Categoria"));
  }

  static void dadosFinanceiros(
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate inicio) {
    if (tipo == null || (tipo != TipoTransacao.RECEITA && tipo != TipoTransacao.DESPESA)) {
      throw new RegraNegocioException(
          "TIPO_TRANSACAO_INVALIDO", "Recorrencias aceitam somente receita ou despesa");
    }
    if (valor == null
        || valor.signum() <= 0
        || valor.scale() > 4
        || valor.setScale(4).precision() > 19) {
      throw new RegraNegocioException("VALOR_TRANSACAO_INVALIDO", "Valor da transacao invalido");
    }
    if (descricao == null || descricao.isBlank() || descricao.strip().length() > 255) {
      throw new RegraNegocioException(
          "DESCRICAO_TRANSACAO_INVALIDA", "Descricao da transacao invalida");
    }
    if (observacoes != null && observacoes.length() > 2000) {
      throw new RegraNegocioException(
          "OBSERVACOES_TRANSACAO_INVALIDAS", "Observacoes da transacao invalidas");
    }
    if (inicio == null) {
      throw new RegraNegocioException("DATA_INICIAL_OBRIGATORIA", "Data inicial obrigatoria");
    }
  }

  static RegraRecorrencia regra(
      LocalDate inicio,
      FrequenciaRecorrencia frequencia,
      Integer intervalo,
      Set<DayOfWeek> diasSemana,
      Set<Integer> diasMes,
      Integer quantidade,
      LocalDate ate) {
    if (frequencia == null) {
      throw new RegraNegocioException("FREQUENCIA_INVALIDA", "Frequencia invalida");
    }
    try {
      return new RegraRecorrencia(
          inicio,
          frequencia,
          intervalo == null ? 1 : intervalo,
          diasSemana,
          diasMes,
          quantidade,
          ate);
    } catch (IllegalArgumentException exception) {
      throw new RegraNegocioException("RRULE_INVALIDA", exception.getMessage());
    }
  }

  static RegraRecorrencia regraRecorrencia(
      LocalDate inicio,
      FrequenciaRecorrencia frequencia,
      Integer intervalo,
      Set<DayOfWeek> diasSemana,
      Set<Integer> diasMes,
      Integer quantidade,
      LocalDate ate) {
    if (frequencia == FrequenciaRecorrencia.MONTHLY && (diasMes == null || diasMes.isEmpty())) {
      throw new RegraNegocioException(
          "RRULE_INVALIDA", "A recorrencia mensal exige ao menos um dia do mes");
    }
    return regra(inicio, frequencia, intervalo, diasSemana, diasMes, quantidade, ate);
  }

  static RecursoNaoEncontradoException naoEncontrado(String recurso) {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrado");
  }
}
