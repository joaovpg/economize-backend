package com.joaovpg.economize.transacao;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.shared.persistence.EntidadeBase;
import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
import com.joaovpg.economize.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TB006_TRANSACAO")
public class Transacao extends EntidadeBase {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ID_USUARIO", nullable = false)
  private Usuario usuario;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ID_CONTA_FINANCEIRA", nullable = false)
  private ContaFinanceira conta;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ID_CATEGORIA")
  private Categoria categoria;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ID_GRUPO_RECORRENCIA")
  private GrupoRecorrencia grupoRecorrencia;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ID_SEGMENTO_RECORRENCIA")
  private SegmentoRecorrencia segmentoRecorrencia;

  @Enumerated(EnumType.STRING)
  @Column(name = "STR_TIPO", nullable = false, length = 20)
  private TipoTransacao tipo;

  @Enumerated(EnumType.STRING)
  @Column(name = "STR_SITUACAO", nullable = false, length = 20)
  private SituacaoTransacao situacao;

  @Column(name = "STR_DESCRICAO", nullable = false)
  private String descricao;

  @Column(name = "STR_OBSERVACOES", length = 2000)
  private String observacoes;

  @Column(name = "DEC_VALOR", nullable = false, precision = 19, scale = 4)
  private BigDecimal valor;

  @Column(name = "DAT_FINANCEIRA", nullable = false)
  private LocalDate dataFinanceira;

  @Column(name = "DAT_IDENTIFICADOR_RECORRENCIA")
  private LocalDate identificadorRecorrencia;

  @Column(name = "DHR_EFETIVACAO")
  private Instant efetivadoEm;

  @Column(name = "BOL_EXCECAO_RECORRENCIA", nullable = false)
  private boolean excecaoRecorrencia;

  /**
   * Origem histórica de uma ocorrência de série desligada e tratada como transação simples. Não
   * vincula a transação à série; apenas preserva de onde ela veio.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ID_SEGMENTO_ORIGEM")
  private SegmentoRecorrencia segmentoOrigem;

  @Column(name = "DAT_ORIGEM_RECORRENCIA")
  private LocalDate dataOriginalOrigem;

  @Column(name = "INT_NUMERO_PARCELA_ORIGEM")
  private Integer numeroParcelaOrigem;
}
