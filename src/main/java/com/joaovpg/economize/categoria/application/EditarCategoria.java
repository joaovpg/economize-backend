package com.joaovpg.economize.categoria.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class EditarCategoria {
  private final CategoriaRepository categoriaRepository;

  public EditarCategoria(CategoriaRepository categoriaRepository) {
    this.categoriaRepository = categoriaRepository;
  }

  @Transactional
  public Categoria executar(Comando comando) {
    var categoria =
        categoriaRepository
            .buscarDoUsuario(comando.categoriaId(), comando.usuarioId())
            .orElseThrow(CadastrarCategoria::naoEncontrada);
    if (comando.ativo() == null) {
      throw new RegraNegocioException("ATIVO_CATEGORIA_INVALIDO", "Ativo da categoria invalido");
    }
    var nome = CategoriaValidation.nome(comando.nome());
    var cor = CategoriaValidation.cor(comando.cor());
    var pai = buscarEValidarPai(comando, categoria);

    if (categoriaRepository.existeComNomeNoMesmoNivel(
        comando.usuarioId(), comando.categoriaPaiId(), nome, categoria.getId())) {
      throw CategoriaValidation.nomeDuplicado();
    }

    validarAtivo(categoria, pai, comando.ativo());

    categoria.setNome(nome);
    categoria.setCor(cor);
    categoria.setCategoriaPai(pai);
    categoria.setAtivo(comando.ativo());

    CategoriaConstraintHandler.flush(categoriaRepository);

    return categoria;
  }

  private Categoria buscarEValidarPai(Comando comando, Categoria categoria) {
    if (comando.categoriaPaiId() == null) {
      return null;
    }
    if (comando.categoriaPaiId().equals(categoria.getId())) {
      throw ciclo();
    }
    var pai =
        categoriaRepository
            .buscarDoUsuario(comando.categoriaPaiId(), comando.usuarioId())
            .orElseThrow(CadastrarCategoria::naoEncontrada);
    boolean mesmoPai =
        categoria.getCategoriaPai() != null
            && categoria.getCategoriaPai().getId().equals(pai.getId());
    if (!pai.isAtivo() && !mesmoPai) {
      throw new RegraNegocioException("CATEGORIA_PAI_INATIVA", "A categoria pai deve estar ativa");
    }
    for (var ancestral = pai; ancestral != null; ancestral = ancestral.getCategoriaPai()) {
      if (ancestral.getId().equals(categoria.getId())) {
        throw ciclo();
      }
    }
    return pai;
  }

  private void validarAtivo(Categoria categoria, Categoria novoPai, boolean ativo) {
    if (ativo == categoria.isAtivo()) {
      return;
    }
    if (!ativo && categoriaRepository.existeDescendenteAtiva(categoria.getId())) {
      throw new RegraNegocioException(
          "CATEGORIA_POSSUI_DESCENDENTE_ATIVA", "A categoria possui descendente ativa");
    }
    if (ativo) {
      for (var ancestral = novoPai; ancestral != null; ancestral = ancestral.getCategoriaPai()) {
        if (!ancestral.isAtivo()) {
          throw new RegraNegocioException(
              "CATEGORIA_POSSUI_ANCESTRAL_INATIVA", "Todos os ancestrais devem estar ativos");
        }
      }
    }
  }

  private RegraNegocioException ciclo() {
    return new RegraNegocioException(
        "HIERARQUIA_CATEGORIA_CICLICA", "A hierarquia da categoria formaria um ciclo");
  }

  public record Comando(
      UUID usuarioId,
      UUID categoriaId,
      String nome,
      String cor,
      UUID categoriaPaiId,
      Boolean ativo) {}
}
