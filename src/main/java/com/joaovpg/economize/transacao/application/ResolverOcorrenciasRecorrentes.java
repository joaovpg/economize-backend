package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SupressaoRecorrencia;
import com.joaovpg.economize.recorrencia.domain.ExpansorRecorrencia;
import com.joaovpg.economize.recorrencia.domain.LeitorRruleRecorrencia;
import com.joaovpg.economize.recorrencia.domain.OcorrenciaRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RegraRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.domain.SerieRecorrencia;
import com.joaovpg.economize.recorrencia.domain.TipoGrupoRecorrencia;
import com.joaovpg.economize.transacao.EscopoEdicaoTransacao;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.OrigemItemConsulta;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class ResolverOcorrenciasRecorrentes {
  private static final List<EscopoEdicaoTransacao> ESCOPOS_DE_SERIE =
      List.of(EscopoEdicaoTransacao.SOMENTE_ESTA, EscopoEdicaoTransacao.ESTA_E_AS_PROXIMAS);

  private final ExpansorRecorrencia expansor;
  private final LeitorRruleRecorrencia leitorRrule;

  public ResolverOcorrenciasRecorrentes(
      ExpansorRecorrencia expansor, LeitorRruleRecorrencia leitorRrule) {
    this.expansor = expansor;
    this.leitorRrule = leitorRrule;
  }

  public List<Resultado> resolver(
      List<SegmentoRecorrencia> segmentos,
      List<Transacao> transacoes,
      List<SupressaoRecorrencia> supressoes,
      LocalDate inicio,
      LocalDate fim) {
    var materializadas =
        transacoes.stream()
            .filter(transacao -> transacao.getSegmentoRecorrencia() != null)
            .collect(
                Collectors.toMap(
                    this::chaveTransacao, Function.identity(), (primeira, _) -> primeira));
    var supridas =
        supressoes.stream()
            .map(
                supressao ->
                    new Chave(
                        supressao.getSegmento().getId(), supressao.getIdentificadorRecorrencia()))
            .collect(Collectors.toSet());
    var resultados = new java.util.ArrayList<Resultado>();
    for (var transacao : transacoes) {
      if (estaNoIntervalo(transacao.getDataFinanceira(), inicio, fim)) {
        resultados.add(materializada(transacao));
      }
    }
    for (var segmento : segmentos) {
      var regra = leitorRrule.lerSegmento(segmento.getInicio(), segmento.getRrule());
      var limite = fim;
      if (segmento.getFim() != null && segmento.getFim().isBefore(limite)) {
        limite = segmento.getFim();
      }
      if (limite.isBefore(segmento.getInicio())) {
        continue;
      }
      var repeticao = RepeticaoRecorrencia.de(segmento, regra);
      for (var ocorrencia :
          expansor.expandir(regra, segmento.getInicio(), limite, segmento.getPoliticaData())) {
        if (inicio != null && ocorrencia.dataOriginal().isBefore(inicio)) {
          continue;
        }
        var chave = new Chave(segmento.getId(), ocorrencia.dataOriginal());
        if (supridas.contains(chave) || materializadas.containsKey(chave)) {
          continue;
        }
        resultados.add(virtual(segmento, repeticao, ocorrencia));
      }
    }
    return resultados;
  }

  private boolean estaNoIntervalo(LocalDate data, LocalDate inicio, LocalDate fim) {
    return data != null
        && (inicio == null || !data.isBefore(inicio))
        && (fim == null || !data.isAfter(fim));
  }

  private Chave chaveTransacao(Transacao transacao) {
    return new Chave(
        transacao.getSegmentoRecorrencia().getId(), transacao.getIdentificadorRecorrencia());
  }

  private Resultado materializada(Transacao transacao) {
    var segmento = transacao.getSegmentoRecorrencia();
    var grupo = transacao.getGrupoRecorrencia();
    if (grupo == null && segmento != null) {
      grupo = segmento.getGrupo();
    }
    var origem =
        grupo != null && grupo.getTipo() == TipoGrupoRecorrencia.PARCELAMENTO
            ? OrigemItemConsulta.PARCELA
            : OrigemItemConsulta.TRANSACAO_RECORRENTE;
    if (segmento == null) {
      return new Resultado(
          origem,
          transacao.getId(),
          transacao.getSituacao(),
          transacao.getDescricao(),
          transacao.getObservacoes(),
          impacto(transacao),
          transacao.getDataFinanceira(),
          transacao.getEfetivadoEm(),
          transacao.getConta().getId(),
          categoriaId(transacao),
          transacao.getTipo(),
          false,
          protegida(transacao),
          IdentidadeEdicaoTransacao.chave(transacao.getId()),
          IdentidadeEdicaoTransacao.versao(transacao.getVersao()),
          null,
          null,
          List.of());
    }
    var regra = leitorRrule.lerSegmento(segmento.getInicio(), segmento.getRrule());
    var numeroParcela =
        segmento.getNumeroPrimeiraParcela() == null
            ? null
            : segmento.getNumeroPrimeiraParcela()
                + ocorrenciaNumero(segmento, regra, transacao.getIdentificadorRecorrencia())
                - 1;
    var totalParcelas =
        grupo.getTipo() != TipoGrupoRecorrencia.PARCELAMENTO
            ? null
            : segmento.getQuantidadeTotalOriginal();
    return new Resultado(
        origem,
        transacao.getId(),
        transacao.getSituacao(),
        transacao.getDescricao(),
        transacao.getObservacoes(),
        impacto(transacao),
        transacao.getDataFinanceira(),
        transacao.getEfetivadoEm(),
        transacao.getConta().getId(),
        categoriaId(transacao),
        transacao.getTipo(),
        false,
        protegida(transacao),
        IdentidadeEdicaoTransacao.chaveRecorrencia(
            segmento.getId(), transacao.getIdentificadorRecorrencia()),
        IdentidadeEdicaoTransacao.versaoRecorrencia(grupo.getRevisaoSerie(), transacao.getVersao()),
        new SerieRecorrencia(
            grupo.getId(),
            segmento.getId(),
            segmento.getInicio(),
            transacao.getIdentificadorRecorrencia(),
            numeroParcela,
            totalParcelas),
        RepeticaoRecorrencia.de(segmento, regra),
        ESCOPOS_DE_SERIE);
  }

  private Resultado virtual(
      SegmentoRecorrencia segmento,
      RepeticaoRecorrencia repeticao,
      OcorrenciaRecorrencia ocorrencia) {
    var grupo = segmento.getGrupo();
    var numeroParcela =
        segmento.getNumeroPrimeiraParcela() == null
            ? null
            : segmento.getNumeroPrimeiraParcela() + ocorrencia.numeroOcorrencia() - 1;
    var parcelado = grupo.getTipo() == TipoGrupoRecorrencia.PARCELAMENTO;
    return new Resultado(
        parcelado ? OrigemItemConsulta.PARCELA : OrigemItemConsulta.TRANSACAO_RECORRENTE,
        null,
        SituacaoTransacao.PLANEJADA,
        segmento.getDescricao(),
        segmento.getObservacoes(),
        segmento.getTipo() == TipoTransacao.RECEITA
            ? segmento.getValor()
            : segmento.getValor().negate(),
        ocorrencia.dataOriginal(),
        null,
        segmento.getConta().getId(),
        segmento.getCategoria() == null ? null : segmento.getCategoria().getId(),
        segmento.getTipo(),
        true,
        false,
        IdentidadeEdicaoTransacao.chaveRecorrencia(segmento.getId(), ocorrencia.dataOriginal()),
        IdentidadeEdicaoTransacao.versaoRecorrencia(grupo.getRevisaoSerie(), null),
        new SerieRecorrencia(
            grupo.getId(),
            segmento.getId(),
            segmento.getInicio(),
            ocorrencia.dataOriginal(),
            numeroParcela,
            parcelado ? segmento.getQuantidadeTotalOriginal() : null),
        repeticao,
        ESCOPOS_DE_SERIE);
  }

  private static UUID categoriaId(Transacao transacao) {
    return transacao.getCategoria() == null ? null : transacao.getCategoria().getId();
  }

  private static boolean protegida(Transacao transacao) {
    return transacao.isExcecaoRecorrencia()
        || transacao.getSituacao() == SituacaoTransacao.EFETIVADA;
  }

  private static BigDecimal impacto(Transacao transacao) {
    return transacao.getTipo() == TipoTransacao.RECEITA
        ? transacao.getValor()
        : transacao.getValor().negate();
  }

  private int ocorrenciaNumero(
      SegmentoRecorrencia segmento, RegraRecorrencia regra, LocalDate data) {
    var resultado =
        expansor.expandir(regra, segmento.getInicio(), data, segmento.getPoliticaData());
    return resultado.stream()
        .filter(ocorrencia -> ocorrencia.dataOriginal().equals(data))
        .mapToInt(OcorrenciaRecorrencia::numeroOcorrencia)
        .findFirst()
        .orElse(1);
  }

  public record Resultado(
      OrigemItemConsulta origem,
      UUID operacaoId,
      SituacaoTransacao situacao,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      Instant efetivadoEm,
      UUID contaId,
      UUID categoriaId,
      TipoTransacao tipo,
      boolean virtual,
      boolean protegida,
      String chaveEdicao,
      String versaoEdicao,
      SerieRecorrencia serie,
      RepeticaoRecorrencia repeticao,
      List<EscopoEdicaoTransacao> escoposDisponiveis) {}

  private record Chave(UUID segmentoId, LocalDate dataOriginal) {}
}
