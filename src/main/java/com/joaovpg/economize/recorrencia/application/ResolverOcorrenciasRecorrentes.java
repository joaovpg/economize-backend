package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.recorrencia.ExpansorRecorrencia;
import com.joaovpg.economize.recorrencia.LeitorRruleRecorrencia;
import com.joaovpg.economize.recorrencia.OcorrenciaRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SupressaoRecorrencia;
import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;
import com.joaovpg.economize.transacao.OrigemItemConsulta;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class ResolverOcorrenciasRecorrentes {
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
        resultados.add(Resultado.materializada(transacao, expansor));
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
      var politica =
          segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.PARCELAMENTO
              ? PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
              : PoliticaDataOcorrencia.PADRAO;
      for (var ocorrencia : expansor.expandir(regra, segmento.getInicio(), limite, politica)) {
        if (inicio != null && ocorrencia.dataOriginal().isBefore(inicio)) {
          continue;
        }
        var chave = new Chave(segmento.getId(), ocorrencia.dataOriginal());
        if (supridas.contains(chave) || materializadas.containsKey(chave)) {
          continue;
        }
        resultados.add(Resultado.virtual(segmento, ocorrencia));
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

  public record Resultado(
      OrigemItemConsulta origem,
      UUID operacaoId,
      SituacaoTransacao situacao,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      java.time.Instant efetivadoEm,
      UUID contaId,
      UUID categoriaId,
      UUID contaContraparteId,
      UUID grupoRecorrenciaId,
      UUID segmentoRecorrenciaId,
      LocalDate dataOriginalRecorrencia,
      Integer numeroParcela,
      String rrule,
      LocalDate inicioRecorrencia,
      PoliticaDataOcorrencia politicaDataOcorrencia) {
    static Resultado materializada(Transacao transacao, ExpansorRecorrencia expansor) {
      var segmento = transacao.getSegmentoRecorrencia();
      var grupo = transacao.getGrupoRecorrencia();
      if (grupo == null && segmento != null) {
        grupo = segmento.getGrupo();
      }
      var origem =
          grupo != null && grupo.getTipo() == TipoGrupoRecorrencia.PARCELAMENTO
              ? OrigemItemConsulta.PARCELA
              : OrigemItemConsulta.TRANSACAO_RECORRENTE;
      var numeroParcela =
          segmento == null || segmento.getNumeroPrimeiraParcela() == null
              ? null
              : segmento.getNumeroPrimeiraParcela()
                  + ocorrenciaNumero(expansor, segmento, transacao.getIdentificadorRecorrencia())
                  - 1;
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
          transacao.getCategoria() == null ? null : transacao.getCategoria().getId(),
          null,
          grupo == null ? null : grupo.getId(),
          segmento == null ? null : segmento.getId(),
          transacao.getIdentificadorRecorrencia(),
          numeroParcela,
          segmento == null ? null : segmento.getRrule(),
          segmento == null ? null : segmento.getInicio(),
          segmento == null ? null : politica(segmento));
    }

    static Resultado virtual(SegmentoRecorrencia segmento, OcorrenciaRecorrencia ocorrencia) {
      var numeroParcela =
          segmento.getNumeroPrimeiraParcela() == null
              ? null
              : segmento.getNumeroPrimeiraParcela() + ocorrencia.numeroOcorrencia() - 1;
      var origem =
          segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.PARCELAMENTO
              ? OrigemItemConsulta.PARCELA
              : OrigemItemConsulta.TRANSACAO_RECORRENTE;
      return new Resultado(
          origem,
          null,
          SituacaoTransacao.PLANEJADA,
          segmento.getDescricao(),
          segmento.getObservacoes(),
          segmento.getTipo() == com.joaovpg.economize.transacao.TipoTransacao.RECEITA
              ? segmento.getValor()
              : segmento.getValor().negate(),
          ocorrencia.dataOriginal(),
          null,
          segmento.getConta().getId(),
          segmento.getCategoria() == null ? null : segmento.getCategoria().getId(),
          null,
          segmento.getGrupo().getId(),
          segmento.getId(),
          ocorrencia.dataOriginal(),
          numeroParcela,
          segmento.getRrule(),
          segmento.getInicio(),
          politica(segmento));
    }

    private static PoliticaDataOcorrencia politica(SegmentoRecorrencia segmento) {
      return segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.PARCELAMENTO
          ? PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
          : PoliticaDataOcorrencia.PADRAO;
    }

    private static BigDecimal impacto(Transacao transacao) {
      return transacao.getTipo() == com.joaovpg.economize.transacao.TipoTransacao.RECEITA
          ? transacao.getValor()
          : transacao.getValor().negate();
    }

    private static int ocorrenciaNumero(
        ExpansorRecorrencia expansor, SegmentoRecorrencia segmento, LocalDate data) {
      var regra =
          new LeitorRruleRecorrencia().lerSegmento(segmento.getInicio(), segmento.getRrule());
      var resultado =
          expansor.expandir(
              regra,
              segmento.getInicio(),
              data,
              segmento.getNumeroPrimeiraParcela() == null
                  ? PoliticaDataOcorrencia.PADRAO
                  : PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES);
      return resultado.stream()
          .filter(ocorrencia -> ocorrencia.dataOriginal().equals(data))
          .mapToInt(OcorrenciaRecorrencia::numeroOcorrencia)
          .findFirst()
          .orElse(1);
    }
  }

  private record Chave(UUID segmentoId, LocalDate dataOriginal) {}
}
