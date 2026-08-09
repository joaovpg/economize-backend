package com.joaovpg.economize.usuario.application;

import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2Factory;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class CadastrarUsuario {
  private final UsuarioRepository usuarioRepository;

  public CadastrarUsuario(UsuarioRepository usuarioRepository) {
    this.usuarioRepository = usuarioRepository;
  }

  @Transactional
  public Resultado executar(Comando comando) {
    var email = comando.email().strip().toLowerCase(Locale.ROOT);

    if (usuarioRepository.buscarPorEmail(email).isPresent()) {
      throw emailJaCadastrado();
    }

    var usuario = new Usuario();
    usuario.setNome(comando.nome().strip());
    usuario.setEmail(email);
    usuario.setTimezone(comando.timezone());
    usuario.setAtivo(true);

    var argon2 = Argon2Factory.create();
    var senha = comando.senha().toCharArray();
    try {
      usuario.setSenhaHash(argon2.hash(2, 19_456, 1, senha));
    } finally {
      argon2.wipeArray(senha);
    }

    try {
      usuarioRepository.persistAndFlush(usuario);
    } catch (ConstraintViolationException exception) {
      if ("uk001_01_email".equalsIgnoreCase(exception.getConstraintName())) {
        throw emailJaCadastrado();
      }
      throw exception;
    }
    var token = Jwt.subject(usuario.getId().toString()).groups(Set.of("usuario")).sign();
    return new Resultado(
        token,
        new UsuarioCadastrado(
            usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTimezone()));
  }

  private RegraNegocioException emailJaCadastrado() {
    return new RegraNegocioException("EMAIL_JA_CADASTRADO", "E-mail ja cadastrado");
  }

  public record Comando(String nome, String email, String senha, String timezone) {}

  public record Resultado(String token, UsuarioCadastrado usuario) {}

  public record UsuarioCadastrado(UUID id, String nome, String email, String timezone) {}
}
