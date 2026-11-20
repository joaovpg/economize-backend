package com.joaovpg.economize.recorrencia;

import com.joaovpg.economize.recorrencia.domain.TipoGrupoRecorrencia;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GrupoRecorrenciaRepository implements PanacheRepositoryBase<GrupoRecorrencia, UUID> {
  public Optional<GrupoRecorrencia> buscarDoUsuario(UUID id, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2", id, usuarioId).firstResultOptional();
  }

  public Optional<GrupoRecorrencia> buscarDoUsuarioParaEdicao(UUID id, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2", id, usuarioId)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .firstResultOptional();
  }

  public List<GrupoRecorrencia> listarDoUsuario(UUID usuarioId, TipoGrupoRecorrencia tipo) {
    return list("usuario.id = ?1 and tipo = ?2 order by criadoEm, id", usuarioId, tipo);
  }
}
