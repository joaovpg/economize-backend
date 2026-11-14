package com.joaovpg.economize.transacao;

import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TransacaoRepository implements PanacheRepositoryBase<Transacao, UUID> {
  public Optional<Transacao> buscarDoUsuario(UUID id, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2", id, usuarioId).firstResultOptional();
  }

  /**
   * Grupo de origem de uma transação simples destacada de série, sem bloquear a transação. Serve
   * para bloquear o grupo antes da transação, na mesma ordem das demais escritas de série.
   */
  public Optional<UUID> buscarGrupoOrigemDoUsuario(UUID id, UUID usuarioId) {
    return getEntityManager()
        .createQuery(
            "select g.id from Transacao t join t.segmentoOrigem s join s.grupo g"
                + " where t.id = :id and t.usuario.id = :usuarioId",
            UUID.class)
        .setParameter("id", id)
        .setParameter("usuarioId", usuarioId)
        .getResultStream()
        .findFirst();
  }

  public Optional<Transacao> buscarDoUsuarioParaEdicao(UUID id, UUID usuarioId) {
    return find("id = ?1 and usuario.id = ?2", id, usuarioId)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .firstResultOptional();
  }

  public Optional<Transacao> buscarOcorrenciaDoUsuarioParaEdicao(
      UUID segmentoId, LocalDate identificador, UUID usuarioId) {
    return find(
            "segmentoRecorrencia.id = ?1 and identificadorRecorrencia = ?2 and usuario.id = ?3",
            segmentoId,
            identificador,
            usuarioId)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .firstResultOptional();
  }

  /** Leitura sem bloqueio para prévias; não autoriza gravação sem nova comparação. */
  public Optional<Transacao> buscarOcorrenciaDoUsuario(
      UUID segmentoId, LocalDate identificador, UUID usuarioId) {
    return find(
            "segmentoRecorrencia.id = ?1 and identificadorRecorrencia = ?2 and usuario.id = ?3",
            segmentoId,
            identificador,
            usuarioId)
        .firstResultOptional();
  }

  /**
   * Registros protegidos de um grupo com data original igual ou posterior à data informada, na
   * ordem da data financeira: materializados do grupo (individualizados ou efetivados) e transações
   * desligadas da série cuja origem pertence ao grupo.
   */
  public List<Transacao> listarProtegidasDoGrupoAPartirDe(
      UUID grupoId, UUID usuarioId, LocalDate dataOriginal) {
    return getEntityManager()
        .createQuery(
            "select t from Transacao t"
                + " left join fetch t.segmentoRecorrencia s"
                + " left join fetch t.segmentoOrigem o"
                + " where t.usuario.id = :usuarioId and ("
                + " (s.grupo.id = :grupoId and t.identificadorRecorrencia >= :dataOriginal"
                + " and (t.excecaoRecorrencia = true or t.situacao = :efetivada))"
                + " or (o.grupo.id = :grupoId and t.dataOriginalOrigem >= :dataOriginal))"
                + " order by t.dataFinanceira, t.id",
            Transacao.class)
        .setParameter("grupoId", grupoId)
        .setParameter("usuarioId", usuarioId)
        .setParameter("dataOriginal", dataOriginal)
        .setParameter("efetivada", SituacaoTransacao.EFETIVADA)
        .getResultList();
  }

  public List<Transacao> listarOcorrenciasDoSegmentoAPartirDe(
      UUID segmentoId, LocalDate inicio, UUID usuarioId) {
    return find(
            "segmentoRecorrencia.id = ?1 and identificadorRecorrencia >= ?2 and usuario.id = ?3"
                + " order by identificadorRecorrencia, id",
            segmentoId,
            inicio,
            usuarioId)
        .list();
  }

  public boolean estaVinculadaATransferencia(UUID transacaoId) {
    return getEntityManager()
            .createQuery(
                """
                select count(f) from Transferencia f
                where f.transacaoSaida.id = :transacaoId or f.transacaoEntrada.id = :transacaoId
                """,
                Long.class)
            .setParameter("transacaoId", transacaoId)
            .getSingleResult()
        > 0;
  }

  public List<Transacao> consultarSimples(
      UUID usuarioId, LocalDate inicio, LocalDate fim, Set<UUID> contaIds, Set<UUID> categoriaIds) {
    var filtros = new ArrayList<String>();
    var parametros = new HashMap<String, Object>();
    adicionarFiltrosSimples(filtros, parametros, usuarioId, contaIds, categoriaIds);
    filtros.add("t.dataFinanceira between :inicio and :fim");
    parametros.put("inicio", inicio);
    parametros.put("fim", fim);
    var hql =
        "select t from Transacao t where "
            + String.join(" and ", filtros)
            + " order by t.dataFinanceira, t.id";
    var consulta = getEntityManager().createQuery(hql, Transacao.class);
    parametros.forEach(consulta::setParameter);
    return consulta.getResultList();
  }

  public List<Transacao> consultarRecorrentes(
      UUID usuarioId, LocalDate inicio, LocalDate fim, Set<UUID> contaIds, Set<UUID> categoriaIds) {
    var filtros = new ArrayList<String>();
    var parametros = new HashMap<String, Object>();
    filtros.add("t.usuario.id = :usuarioId");
    filtros.add("t.segmentoRecorrencia is not null");
    filtros.add("t.tipo in :tiposFinanceiros");
    filtros.add("t.dataFinanceira between :inicio and :fim");
    parametros.put("usuarioId", usuarioId);
    parametros.put("tiposFinanceiros", Set.of(TipoTransacao.RECEITA, TipoTransacao.DESPESA));
    parametros.put("inicio", inicio);
    parametros.put("fim", fim);
    adicionarFiltrosContaCategoria(filtros, parametros, contaIds, categoriaIds);
    return executarConsulta(filtros, parametros);
  }

  public List<Transacao> consultarRecorrentesAte(
      UUID usuarioId, LocalDate fim, Set<UUID> contaIds, Set<UUID> categoriaIds) {
    var filtros = new ArrayList<String>();
    var parametros = new HashMap<String, Object>();
    filtros.add("t.usuario.id = :usuarioId");
    filtros.add("t.segmentoRecorrencia is not null");
    filtros.add("t.tipo in :tiposFinanceiros");
    filtros.add("t.dataFinanceira <= :fim");
    parametros.put("usuarioId", usuarioId);
    parametros.put("tiposFinanceiros", Set.of(TipoTransacao.RECEITA, TipoTransacao.DESPESA));
    parametros.put("fim", fim);
    adicionarFiltrosContaCategoria(filtros, parametros, contaIds, categoriaIds);
    return executarConsulta(filtros, parametros);
  }

  public List<Transacao> consultarRecorrentesDosSegmentos(UUID usuarioId, Set<UUID> segmentoIds) {
    if (segmentoIds.isEmpty()) {
      return List.of();
    }
    var filtros = new ArrayList<String>();
    var parametros = new HashMap<String, Object>();
    filtros.add("t.usuario.id = :usuarioId");
    filtros.add("t.segmentoRecorrencia.id in :segmentoIds");
    filtros.add("t.tipo in :tiposFinanceiros");
    parametros.put("usuarioId", usuarioId);
    parametros.put("segmentoIds", segmentoIds);
    parametros.put("tiposFinanceiros", Set.of(TipoTransacao.RECEITA, TipoTransacao.DESPESA));
    return executarConsulta(filtros, parametros);
  }

  private void adicionarFiltrosContaCategoria(
      List<String> filtros,
      Map<String, Object> parametros,
      Set<UUID> contaIds,
      Set<UUID> categoriaIds) {
    if (!contaIds.isEmpty()) {
      filtros.add("t.conta.id in :contaIds");
      parametros.put("contaIds", contaIds);
    }
    if (!categoriaIds.isEmpty()) {
      filtros.add("t.categoria.id in :categoriaIds");
      parametros.put("categoriaIds", categoriaIds);
    }
  }

  private List<Transacao> executarConsulta(List<String> filtros, Map<String, Object> parametros) {
    var consulta =
        getEntityManager()
            .createQuery(
                "select t from Transacao t join fetch t.segmentoRecorrencia "
                    + "left join fetch t.grupoRecorrencia where "
                    + String.join(" and ", filtros)
                    + " order by t.dataFinanceira, t.id",
                Transacao.class);
    parametros.forEach(consulta::setParameter);
    return consulta.getResultList();
  }

  public BigDecimal somarImpactoSimplesAte(
      UUID usuarioId, LocalDate fim, Set<UUID> contaIds, Set<UUID> categoriaIds) {
    var filtros = new ArrayList<String>();
    var parametros = new HashMap<String, Object>();
    adicionarFiltrosSimples(filtros, parametros, usuarioId, contaIds, categoriaIds);
    filtros.add("t.dataFinanceira <= :fim");
    parametros.put("fim", fim);
    parametros.put("receita", TipoTransacao.RECEITA);

    var hql =
        "select sum(case when t.tipo = :receita then t.valor else -t.valor end) "
            + "from Transacao t where "
            + String.join(" and ", filtros);
    var consulta = getEntityManager().createQuery(hql, BigDecimal.class);
    parametros.forEach(consulta::setParameter);
    var resultado = consulta.getSingleResult();
    return resultado == null ? BigDecimal.ZERO : resultado;
  }

  private void adicionarFiltrosSimples(
      List<String> filtros,
      Map<String, Object> parametros,
      UUID usuarioId,
      Set<UUID> contaIds,
      Set<UUID> categoriaIds) {
    filtros.add("t.usuario.id = :usuarioId");
    filtros.add("t.tipo in :tiposSimples");
    filtros.add("t.grupoRecorrencia is null and t.segmentoRecorrencia is null");
    filtros.add(
        "not exists (select 1 from Transferencia f where f.transacaoSaida = t or f.transacaoEntrada"
            + " = t)");
    parametros.put("usuarioId", usuarioId);
    parametros.put("tiposSimples", Set.of(TipoTransacao.RECEITA, TipoTransacao.DESPESA));

    if (!contaIds.isEmpty()) {
      filtros.add("t.conta.id in :contaIds");
      parametros.put("contaIds", contaIds);
    }
    if (!categoriaIds.isEmpty()) {
      filtros.add("t.categoria.id in :categoriaIds");
      parametros.put("categoriaIds", categoriaIds);
    }
  }
}
