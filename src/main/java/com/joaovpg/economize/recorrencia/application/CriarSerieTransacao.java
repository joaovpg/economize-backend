package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.usuario.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cria o Grupo e o Segmento de uma repetição ou parcelamento iniciado por uma transação. Deve ser
 * chamado dentro da transação do caso de uso que o origina.
 */
@ApplicationScoped
public class CriarSerieTransacao {
  private final GrupoRecorrenciaRepository grupoRepository;
  private final SegmentoRecorrenciaRepository segmentoRepository;

  public CriarSerieTransacao(
      GrupoRecorrenciaRepository grupoRepository,
      SegmentoRecorrenciaRepository segmentoRepository) {
    this.grupoRepository = grupoRepository;
    this.segmentoRepository = segmentoRepository;
  }

  public SegmentoRecorrencia executar(Comando comando) {
    var repeticao = comando.repeticao();
    var regra = RepeticaoValidacao.validar(repeticao, comando.dataFinanceira());
    var parcelamento = repeticao.parcelamento();
    var parcelado = parcelamento != null;

    var grupo = new GrupoRecorrencia();
    grupo.setUsuario(comando.usuario());
    grupo.setDescricao(comando.descricao());
    grupo.setTipo(parcelado ? TipoGrupoRecorrencia.PARCELAMENTO : TipoGrupoRecorrencia.RECORRENCIA);
    grupo.setStatus(StatusRecorrencia.ATIVO);
    grupoRepository.persist(grupo);

    var segmento = new SegmentoRecorrencia();
    segmento.setUsuario(comando.usuario());
    segmento.setGrupo(grupo);
    segmento.setConta(comando.conta());
    segmento.setCategoria(comando.categoria());
    segmento.setTipo(comando.tipo());
    segmento.setDescricao(comando.descricao());
    segmento.setObservacoes(comando.observacoes());
    segmento.setValor(comando.valor());
    segmento.setInicio(comando.dataFinanceira());
    segmento.setFim(regra.ate());
    segmento.setRrule(regra.rrule());
    segmento.setTotalOcorrencias(regra.quantidadeOcorrencias());
    if (parcelado) {
      segmento.setNumeroPrimeiraParcela(parcelamento.numeroPrimeiraParcela());
      segmento.setQuantidadeTotalOriginal(parcelamento.totalParcelas());
    }
    segmento.setPoliticaData(repeticao.politicaData());
    segmento.setStatus(StatusRecorrencia.ATIVO);
    segmentoRepository.persist(segmento);
    return segmento;
  }

  /** Dados já normalizados pelo caso de uso de transação (textos sem espaços nas extremidades). */
  public record Comando(
      Usuario usuario,
      ContaFinanceira conta,
      Categoria categoria,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      RepeticaoRecorrencia repeticao) {}
}
