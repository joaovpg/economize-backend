package com.joaovpg.economize.categoria.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ListarCategorias {
  private final CategoriaRepository categoriaRepository;

  public ListarCategorias(CategoriaRepository categoriaRepository) {
    this.categoriaRepository = categoriaRepository;
  }

  @Transactional
  public List<Categoria> executar(UUID usuarioId, Boolean ativo) {
    return categoriaRepository.listarDoUsuario(usuarioId, ativo).stream().toList();
  }
}
