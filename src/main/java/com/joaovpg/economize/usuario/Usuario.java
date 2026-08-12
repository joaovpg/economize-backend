package com.joaovpg.economize.usuario;

import com.joaovpg.economize.shared.persistence.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TB001_USUARIO")
public class Usuario extends EntidadeBase {
  @Column(name = "STR_NOME", nullable = false, length = 120)
  private String nome;

  @Column(name = "STR_EMAIL", nullable = false, length = 320)
  private String email;

  @Column(name = "STR_SENHA_HASH", nullable = false)
  private String senhaHash;

  @Column(name = "STR_TIMEZONE", nullable = false, length = 80)
  private String timezone;

  @Column(name = "BOL_ATIVO", nullable = false)
  private boolean ativo = true;

  @Column(name = "DHR_EXCLUSAO")
  private Instant excluidoEm;
}
