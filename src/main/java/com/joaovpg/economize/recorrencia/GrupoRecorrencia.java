package com.joaovpg.economize.recorrencia;

import com.joaovpg.economize.recorrencia.domain.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.domain.TipoGrupoRecorrencia;
import com.joaovpg.economize.shared.persistence.EntidadeBase;
import com.joaovpg.economize.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TB004_GRUPO_RECORRENCIA")
public class GrupoRecorrencia extends EntidadeBase {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ID_USUARIO", nullable = false)
  private Usuario usuario;

  @Column(name = "STR_DESCRICAO", nullable = false)
  private String descricao;

  @Enumerated(EnumType.STRING)
  @Column(name = "STR_TIPO", nullable = false, length = 20)
  private TipoGrupoRecorrencia tipo = TipoGrupoRecorrencia.RECORRENCIA;

  @Enumerated(EnumType.STRING)
  @Column(name = "STR_STATUS", nullable = false, length = 20)
  private StatusRecorrencia status;

  @Column(name = "VER_REVISAO_SERIE", nullable = false)
  private long revisaoSerie;
}
