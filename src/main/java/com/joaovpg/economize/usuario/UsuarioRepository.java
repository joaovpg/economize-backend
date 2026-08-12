package com.joaovpg.economize.usuario;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UsuarioRepository implements PanacheRepositoryBase<Usuario, UUID> {
  public Optional<Usuario> buscarPorEmail(String email) {
    return find("email", email.strip().toLowerCase()).firstResultOptional();
  }

  public Optional<Usuario> buscarAtivo(UUID usuarioId) {
    return find("id = ?1 and ativo = true", usuarioId).firstResultOptional();
  }
}
