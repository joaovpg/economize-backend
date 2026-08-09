package com.joaovpg.economize.categoria.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.usuario.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class CadastrarCategoria {
  private final UsuarioRepository usuarioRepository;
  private final CategoriaRepository categoriaRepository;

  public CadastrarCategoria(
      UsuarioRepository usuarioRepository, CategoriaRepository categoriaRepository) {
    this.usuarioRepository = usuarioRepository;
    this.categoriaRepository = categoriaRepository;
  }

  @Transactional
  public Categoria executar(Comando comando) {
    var nome = CategoriaValidation.nome(comando.nome());
    var cor = CategoriaValidation.cor(comando.cor());
    var categoriaPai = comando.categoriaPaiId();
    var usuarioId = comando.usuarioId();

    var usuario =
        usuarioRepository.buscarAtivo(usuarioId).orElseThrow(CadastrarCategoria::naoEncontrada);

    var pai =
        categoriaPai == null
            ? null
            : categoriaRepository
                .buscarAtivaDoUsuario(categoriaPai, usuarioId)
                .orElseThrow(CadastrarCategoria::naoEncontrada);

    if (categoriaRepository.existeComNomeNoMesmoNivel(usuarioId, categoriaPai, nome, null)) {
      throw CategoriaValidation.nomeDuplicado();
    }

    var categoria = new Categoria();

    categoria.setUsuario(usuario);
    categoria.setCategoriaPai(pai);
    categoria.setNome(nome);
    categoria.setCor(cor);

    categoriaRepository.persist(categoria);
    CategoriaConstraintHandler.flush(categoriaRepository);

    return categoria;
  }

  static RecursoNaoEncontradoException naoEncontrada() {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Categoria nao encontrada");
  }

  public record Comando(UUID usuarioId, String nome, String cor, UUID categoriaPaiId) {}
}
