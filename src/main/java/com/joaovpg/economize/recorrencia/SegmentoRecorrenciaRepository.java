package com.joaovpg.economize.recorrencia;

import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class SegmentoRecorrenciaRepository
    implements PanacheRepositoryBase<SegmentoRecorrencia, UUID> {
  public Optional<SegmentoRecorrencia> buscarDoUsuario(UUID id, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2", id, usuarioId).firstResultOptional();
  }

  /** Identifica o grupo do segmento sem carregar a entidade, para bloquear o grupo antes dela. */
  public Optional<UUID> buscarGrupoIdDoUsuario(UUID id, UUID usuarioId) {
    return getEntityManager()
        .createQuery(
            "select s.grupo.id from SegmentoRecorrencia s"
                + " where s.id = :id and s.usuario.id = :usuarioId",
            UUID.class)
        .setParameter("id", id)
        .setParameter("usuarioId", usuarioId)
        .getResultStream()
        .findFirst();
  }

  public Optional<SegmentoRecorrencia> buscarDoUsuarioParaEdicao(UUID id, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2", id, usuarioId)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .firstResultOptional();
  }

  public boolean existeSegmentoPosteriorAtivo(
      UUID grupoId, UUID segmentoId, UUID usuarioId, LocalDate inicio) {
    return count(
            "grupo.id = ?1 and grupo.usuario.id = ?2 and id <> ?3 and inicio > ?4 and status <> ?5",
            grupoId,
            usuarioId,
            segmentoId,
            inicio,
            StatusRecorrencia.CANCELADO)
        > 0;
  }

  public List<SegmentoRecorrencia> listarAtivosDoGrupoAPartirDe(
      UUID grupoId, UUID segmentoId, UUID usuarioId, LocalDate inicio) {
    return find(
            "grupo.id = ?1 and usuario.id = ?2 and id <> ?3 and status = ?4 and inicio >= ?5"
                + " order by inicio, id",
            grupoId,
            usuarioId,
            segmentoId,
            StatusRecorrencia.ATIVO,
            inicio)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .list();
  }

  public boolean existeSegmentoAtivoDoGrupo(UUID grupoId, UUID usuarioId) {
    return count(
            "grupo.id = ?1 and usuario.id = ?2 and status = ?3",
            grupoId,
            usuarioId,
            StatusRecorrencia.ATIVO)
        > 0;
  }

  public boolean existeSegmentoAnteriorDoGrupo(UUID grupoId, UUID usuarioId, LocalDate inicio) {
    return count(
            "grupo.id = ?1 and usuario.id = ?2 and status <> ?3 and inicio < ?4",
            grupoId,
            usuarioId,
            StatusRecorrencia.CANCELADO,
            inicio)
        > 0;
  }

  public List<SegmentoRecorrencia> consultarProjetaveis(
      UUID usuarioId, LocalDate inicio, LocalDate fim, Set<UUID> contaIds, Set<UUID> categoriaIds) {
    var filtros = new ArrayList<String>();
    var parametros = new HashMap<String, Object>();
    filtros.add("s.usuario.id = :usuarioId");
    filtros.add("s.status <> :statusCancelado");
    filtros.add("s.grupo.status <> :grupoCancelado");
    filtros.add("s.inicio <= :fim");
    filtros.add("(s.fim is null or s.fim >= :inicio)");
    parametros.put("usuarioId", usuarioId);
    parametros.put("statusCancelado", StatusRecorrencia.CANCELADO);
    parametros.put("grupoCancelado", StatusRecorrencia.CANCELADO);
    parametros.put("inicio", inicio);
    parametros.put("fim", fim);
    adicionarFiltros(filtros, parametros, contaIds, categoriaIds);
    return executarConsulta(filtros, parametros);
  }

  public List<SegmentoRecorrencia> consultarProjetaveisAte(
      UUID usuarioId, LocalDate fim, Set<UUID> contaIds, Set<UUID> categoriaIds) {
    var filtros = new ArrayList<String>();
    var parametros = new HashMap<String, Object>();
    filtros.add("s.usuario.id = :usuarioId");
    filtros.add("s.status <> :statusCancelado");
    filtros.add("s.grupo.status <> :grupoCancelado");
    filtros.add("s.inicio <= :fim");
    parametros.put("usuarioId", usuarioId);
    parametros.put("statusCancelado", StatusRecorrencia.CANCELADO);
    parametros.put("grupoCancelado", StatusRecorrencia.CANCELADO);
    parametros.put("fim", fim);
    adicionarFiltros(filtros, parametros, contaIds, categoriaIds);
    return executarConsulta(filtros, parametros);
  }

  private void adicionarFiltros(
      List<String> filtros,
      Map<String, Object> parametros,
      Set<UUID> contaIds,
      Set<UUID> categoriaIds) {
    if (!contaIds.isEmpty()) {
      filtros.add("s.conta.id in :contaIds");
      parametros.put("contaIds", contaIds);
    }
    if (!categoriaIds.isEmpty()) {
      filtros.add("s.categoria.id in :categoriaIds");
      parametros.put("categoriaIds", categoriaIds);
    }
  }

  private List<SegmentoRecorrencia> executarConsulta(
      List<String> filtros, Map<String, Object> parametros) {
    var consulta =
        getEntityManager()
            .createQuery(
                "select distinct s from SegmentoRecorrencia s "
                    + "join fetch s.grupo join fetch s.conta left join fetch s.categoria where "
                    + String.join(" and ", filtros)
                    + " order by s.inicio, s.id",
                SegmentoRecorrencia.class);
    parametros.forEach(consulta::setParameter);
    return consulta.getResultList();
  }
}
