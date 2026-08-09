package com.joaovpg.economize.categoria;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CategoriaRepository implements PanacheRepositoryBase<Categoria, UUID> {
  public Optional<Categoria> buscarAtivaDoUsuario(UUID categoriaId, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2 and ativo = true", categoriaId, usuarioId)
        .firstResultOptional();
  }

  public Optional<Categoria> buscarDoUsuario(UUID categoriaId, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2", categoriaId, usuarioId).firstResultOptional();
  }

  public long contarDoUsuario(UUID usuarioId, Set<UUID> categoriaIds) {
    return count("usuario.id = ?1 and id in ?2", usuarioId, categoriaIds);
  }

  public boolean existeComNomeNoMesmoNivel(
      UUID usuarioId, UUID categoriaPaiId, String nome, UUID ignorarId) {
    return count(
            """
            usuario.id = ?1
            and (
              (?2 is null and categoriaPai is null)
              or categoriaPai.id = ?2)
            and lower(nome) = lower(?3)
            and (?4 is null or id <> ?4)
            """,
            usuarioId,
            categoriaPaiId,
            nome,
            ignorarId)
        > 0;
  }

  public List<Categoria> listarDoUsuario(UUID usuarioId, Boolean ativo) {
    return list(
        """
             select c from Categoria c left join fetch c.categoriaPai
             where c.usuario.id = ?1
             and (?2 is null or c.ativo = ?2)
             order by lower(c.nome), c.id
        """,
        usuarioId,
        ativo);
  }

  public boolean existeDescendenteAtiva(UUID categoriaId) {
    Number quantidade =
        (Number)
            getEntityManager()
                .createNativeQuery(
                    """
                    WITH RECURSIVE descendentes AS (
                        SELECT ID_REGISTRO, BOL_ATIVO FROM TB003_CATEGORIA WHERE ID_CATEGORIA_PAI = :categoriaId
                        UNION ALL
                        SELECT categoria.ID_REGISTRO, categoria.BOL_ATIVO
                        FROM TB003_CATEGORIA categoria
                        JOIN descendentes pai ON categoria.ID_CATEGORIA_PAI = pai.ID_REGISTRO
                    )
                    SELECT COUNT(*) FROM descendentes WHERE BOL_ATIVO = TRUE
                    """)
                .setParameter("categoriaId", categoriaId)
                .getSingleResult();
    return quantidade.longValue() > 0;
  }
}
