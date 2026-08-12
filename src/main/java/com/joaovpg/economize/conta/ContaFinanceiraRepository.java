package com.joaovpg.economize.conta;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ContaFinanceiraRepository implements PanacheRepositoryBase<ContaFinanceira, UUID> {
  public Optional<ContaFinanceira> buscarAtivaDoUsuario(UUID contaId, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2 and ativo = true", contaId, usuarioId)
        .firstResultOptional();
  }

  public Optional<ContaFinanceira> buscarDoUsuarioParaEdicao(UUID contaId, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2", contaId, usuarioId)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .firstResultOptional();
  }

  public boolean existeComNome(UUID usuarioId, String nome) {
    return count("usuario.id = ?1 and lower(nome) = lower(?2)", usuarioId, nome) > 0;
  }

  public boolean existeOutroComNome(UUID usuarioId, String nome, UUID ignorarId) {
    return count(
            "usuario.id = ?1 and lower(nome) = lower(?2) and id <> ?3", usuarioId, nome, ignorarId)
        > 0;
  }

  public List<ContaFinanceira> listarDoUsuario(UUID usuarioId) {
    return list("usuario.id = ?1 order by lower(nome), id", usuarioId);
  }

  public List<ContaFinanceira> listarDoUsuario(UUID usuarioId, Set<UUID> contaIds) {
    return list("usuario.id = ?1 and id in ?2 order by lower(nome), id", usuarioId, contaIds);
  }

  public List<ContaFinanceira> listarDoUsuario(UUID usuarioId, Boolean ativo) {
    return list("usuario.id = ?1 and ativo = ?2 order by lower(nome), id", usuarioId, ativo);
  }
}
