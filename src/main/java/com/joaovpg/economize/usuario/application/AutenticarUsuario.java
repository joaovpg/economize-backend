package com.joaovpg.economize.usuario.application;

import com.joaovpg.economize.shared.exception.AutenticacaoException;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import de.mkammerer.argon2.Argon2Factory;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
public class AutenticarUsuario {
  private static final String HASH_COMPARACAO =
      Argon2Factory.create().hash(2, 19_456, 1, "credencial-inexistente".toCharArray());
  private final UsuarioRepository usuarioRepository;

  public AutenticarUsuario(UsuarioRepository usuarioRepository) {
    this.usuarioRepository = usuarioRepository;
  }

  public Resultado executar(Comando comando) {
    var usuario = usuarioRepository.buscarPorEmail(comando.email()).filter(Usuario::isAtivo);
    var argon2 = Argon2Factory.create();
    var senha = comando.senha().toCharArray();
    try {
      var senhaValida =
          argon2.verify(usuario.map(Usuario::getSenhaHash).orElse(HASH_COMPARACAO), senha);
      if (usuario.isEmpty() || !senhaValida) {
        throw new AutenticacaoException("CREDENCIAIS_INVALIDAS", "E-mail ou senha invalidos");
      }
    } finally {
      argon2.wipeArray(senha);
    }
    var token =
        Jwt.subject(usuario.orElseThrow().getId().toString()).groups(Set.of("usuario")).sign();
    return new Resultado(token);
  }

  public record Comando(String email, String senha) {}

  public record Resultado(String token) {}
}
