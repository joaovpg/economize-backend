package com.joaovpg.economize.recorrencia;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.shared.persistence.EntidadeBase;
import com.joaovpg.economize.transacao.TipoTransacao;
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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TB005_SEGMENTO_RECORRENCIA")
public class SegmentoRecorrencia extends EntidadeBase {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ID_USUARIO", nullable = false)
  private Usuario usuario;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ID_GRUPO_RECORRENCIA", nullable = false)
  private GrupoRecorrencia grupo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ID_CONTA_FINANCEIRA", nullable = false)
  private ContaFinanceira conta;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ID_CATEGORIA")
  private Categoria categoria;

  @Enumerated(EnumType.STRING)
  @Column(name = "STR_TIPO_TRANSACAO", nullable = false, length = 20)
  private TipoTransacao tipo;

  @Column(name = "STR_DESCRICAO", nullable = false)
  private String descricao;

  @Column(name = "STR_OBSERVACOES", length = 2000)
  private String observacoes;

  @Column(name = "DEC_VALOR", nullable = false, precision = 19, scale = 4)
  private BigDecimal valor;

  @Column(name = "DAT_DTSTART", nullable = false)
  private LocalDate inicio;

  @Column(name = "DAT_FIM")
  private LocalDate fim;

  @Column(name = "STR_RRULE", nullable = false, length = 500)
  private String rrule;

  @Column(name = "INT_TOTAL_OCORRENCIAS")
  private Integer totalOcorrencias;

  @Column(name = "INT_NUMERO_PRIMEIRA_PARCELA")
  private Integer numeroPrimeiraParcela;

  @Column(name = "INT_QUANTIDADE_TOTAL_ORIGINAL")
  private Integer quantidadeTotalOriginal;

  @Enumerated(EnumType.STRING)
  @Column(name = "STR_POLITICA_DATA", nullable = false, length = 30)
  private PoliticaDataOcorrencia politicaData;

  @Enumerated(EnumType.STRING)
  @Column(name = "STR_STATUS", nullable = false, length = 20)
  private StatusRecorrencia status;
}
