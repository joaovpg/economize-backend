package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SupressaoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.application.ResolverOcorrenciasRecorrentes;
import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.ValidacaoException;
import com.joaovpg.economize.transacao.OrigemItemConsulta;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.transferencia.Transferencia;
import com.joaovpg.economize.transferencia.TransferenciaRepository;
import com.joaovpg.economize.usuario.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ConsultarTransacoes {
  private static final int ESCALA_MONETARIA = 4;
  private static final int MAXIMO_MESES = 12;

  private final UsuarioRepository usuarioRepository;
  private final ContaFinanceiraRepository contaRepository;
  private final CategoriaRepository categoriaRepository;
  private final TransacaoRepository transacaoRepository;
  private final TransferenciaRepository transferenciaRepository;
  private final SegmentoRecorrenciaRepository segmentoRepository;
  private final SupressaoRecorrenciaRepository supressaoRepository;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;

  public ConsultarTransacoes(
      UsuarioRepository usuarioRepository,
      ContaFinanceiraRepository contaRepository,
      CategoriaRepository categoriaRepository,
      TransacaoRepository transacaoRepository,
      TransferenciaRepository transferenciaRepository,
      SegmentoRecorrenciaRepository segmentoRepository,
      SupressaoRecorrenciaRepository supressaoRepository,
      ResolverOcorrenciasRecorrentes resolverOcorrencias) {
    this.usuarioRepository = usuarioRepository;
    this.contaRepository = contaRepository;
    this.categoriaRepository = categoriaRepository;
    this.transacaoRepository = transacaoRepository;
    this.transferenciaRepository = transferenciaRepository;
    this.segmentoRepository = segmentoRepository;
    this.supressaoRepository = supressaoRepository;
    this.resolverOcorrencias = resolverOcorrencias;
  }

  @Transactional(Transactional.TxType.SUPPORTS)
  public Resultado executar(Comando comando) {
    usuarioRepository.buscarAtivo(comando.usuarioId()).orElseThrow(this::recursoNaoEncontrado);
    var periodo = resolverPeriodo(comando.inicio(), comando.fim());
    var contaIds = conjunto(comando.contaIds());
    var categoriaIds = conjunto(comando.categoriaIds());

    var contas =
        contaIds.isEmpty()
            ? contaRepository.listarDoUsuario(comando.usuarioId())
            : contaRepository.listarDoUsuario(comando.usuarioId(), contaIds);

    if (contas.size() != contaIds.size() && !contaIds.isEmpty()) {
      throw recursoNaoEncontrado();
    }

    if (!categoriaIds.isEmpty()
        && categoriaRepository.contarDoUsuario(comando.usuarioId(), categoriaIds)
            != categoriaIds.size()) {
      throw recursoNaoEncontrado();
    }

    var transacoes =
        transacaoRepository.consultarSimples(
            comando.usuarioId(),
            periodo.primeiroDia(),
            periodo.ultimoDia(),
            contaIds,
            categoriaIds);

    var segmentos =
        segmentoRepository.consultarProjetaveis(
            comando.usuarioId(),
            periodo.primeiroDia(),
            periodo.ultimoDia(),
            contaIds,
            categoriaIds);

    var transacoesRecorrentes =
        unirTransacoesRecorrentes(
            transacaoRepository.consultarRecorrentes(
                comando.usuarioId(),
                periodo.primeiroDia(),
                periodo.ultimoDia(),
                contaIds,
                categoriaIds),
            transacaoRepository.consultarRecorrentesDosSegmentos(
                comando.usuarioId(), idsSegmentos(segmentos)));

    var recorrenciasItens =
        resolverOcorrencias
            .resolver(
                segmentos,
                transacoesRecorrentes,
                supressoes(segmentos, comando.usuarioId()),
                periodo.primeiroDia(),
                periodo.ultimoDia())
            .stream()
            .filter(resultado -> atendeFiltros(resultado, contaIds, categoriaIds))
            .toList();
    var incluirTransferencias = categoriaIds.isEmpty();
    var transferencias =
        incluirTransferencias
            ? transferenciaRepository.consultar(
                comando.usuarioId(), periodo.primeiroDia(), periodo.ultimoDia(), contaIds)
            : List.<Transferencia>of();

    var saldoInicial =
        contas.stream()
            .filter(conta -> !conta.getDataSaldoInicial().isAfter(periodo.dataSaldoAbertura()))
            .map(ContaFinanceira::getSaldoInicial)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var impactoAnterior =
        transacaoRepository.somarImpactoSimplesAte(
            comando.usuarioId(), periodo.dataSaldoAbertura(), contaIds, categoriaIds);
    var impactoTransferenciasAnterior =
        incluirTransferencias
            ? transferenciaRepository.somarImpactoAte(
                comando.usuarioId(), periodo.dataSaldoAbertura(), contaIds)
            : BigDecimal.ZERO;
    var segmentosAnteriores =
        segmentoRepository.consultarProjetaveisAte(
            comando.usuarioId(), periodo.dataSaldoAbertura(), contaIds, categoriaIds);

    var transacoesRecorrentesAnteriores =
        unirTransacoesRecorrentes(
            transacaoRepository.consultarRecorrentesAte(
                comando.usuarioId(), periodo.dataSaldoAbertura(), contaIds, categoriaIds),
            transacaoRepository.consultarRecorrentesDosSegmentos(
                comando.usuarioId(), idsSegmentos(segmentosAnteriores)));

    var impactoRecorrenciasAnterior =
        resolverOcorrencias
            .resolver(
                segmentosAnteriores,
                transacoesRecorrentesAnteriores,
                supressoes(segmentosAnteriores, comando.usuarioId()),
                null,
                periodo.dataSaldoAbertura())
            .stream()
            .filter(resultado -> atendeFiltros(resultado, contaIds, categoriaIds))
            .map(ResolverOcorrenciasRecorrentes.Resultado::valor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var itens = new ArrayList<Item>();
    transacoes.stream()
        .map(
            transacao ->
                new Item(
                    OrigemItemConsulta.TRANSACAO_SIMPLES,
                    transacao.getId(),
                    transacao.getSituacao(),
                    transacao.getDescricao(),
                    transacao.getObservacoes(),
                    impacto(transacao.getTipo(), transacao.getValor()),
                    transacao.getDataFinanceira(),
                    transacao.getEfetivadoEm(),
                    transacao.getConta().getId(),
                    transacao.getCategoria() == null ? null : transacao.getCategoria().getId(),
                    null))
        .forEach(itens::add);

    transferencias.forEach(
        transferencia -> {
          if (contaIds.isEmpty() || contaIds.contains(transferencia.getContaOrigem().getId())) {
            var saida = transferencia.getTransacaoSaida();
            itens.add(
                new Item(
                    OrigemItemConsulta.TRANSFERENCIA,
                    transferencia.getId(),
                    saida.getSituacao(),
                    saida.getDescricao(),
                    saida.getObservacoes(),
                    saida.getValor().negate(),
                    saida.getDataFinanceira(),
                    saida.getEfetivadoEm(),
                    transferencia.getContaOrigem().getId(),
                    null,
                    transferencia.getContaDestino().getId()));
          }
          if (contaIds.isEmpty() || contaIds.contains(transferencia.getContaDestino().getId())) {
            var entrada = transferencia.getTransacaoEntrada();
            itens.add(
                new Item(
                    OrigemItemConsulta.TRANSFERENCIA,
                    transferencia.getId(),
                    entrada.getSituacao(),
                    entrada.getDescricao(),
                    entrada.getObservacoes(),
                    entrada.getValor(),
                    entrada.getDataFinanceira(),
                    entrada.getEfetivadoEm(),
                    transferencia.getContaDestino().getId(),
                    null,
                    transferencia.getContaOrigem().getId()));
          }
        });

    recorrenciasItens.stream().map(this::item).forEach(itens::add);

    contas.stream()
        .filter(conta -> !conta.getDataSaldoInicial().isBefore(periodo.primeiroDia()))
        .filter(conta -> !conta.getDataSaldoInicial().isAfter(periodo.ultimoDia()))
        .filter(conta -> conta.getSaldoInicial().signum() != 0)
        .map(
            conta ->
                new Item(
                    OrigemItemConsulta.SALDO_INICIAL_CONTA,
                    conta.getId(),
                    null,
                    "Saldo inicial",
                    null,
                    conta.getSaldoInicial(),
                    conta.getDataSaldoInicial(),
                    null,
                    conta.getId(),
                    null,
                    null))
        .forEach(itens::add);

    itens.sort(
        Comparator.comparing(Item::dataFinanceira)
            .thenComparingInt(
                item -> item.origem() == OrigemItemConsulta.SALDO_INICIAL_CONTA ? 0 : 1)
            .thenComparing(Item::operacaoId, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(
                Item::segmentoRecorrenciaId, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(
                Item::dataOriginalRecorrencia, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingInt(
                item ->
                    item.origem() == OrigemItemConsulta.TRANSFERENCIA && item.valor().signum() > 0
                        ? 1
                        : 0)
            .thenComparing(Item::contaId));

    return new Resultado(
        periodo.inicio(),
        periodo.fim(),
        monetario(
            saldoInicial
                .add(impactoAnterior)
                .add(impactoTransferenciasAnterior)
                .add(impactoRecorrenciasAnterior)),
        List.copyOf(itens));
  }

  private List<com.joaovpg.economize.recorrencia.SupressaoRecorrencia> supressoes(
      List<SegmentoRecorrencia> segmentos, UUID usuarioId) {
    return supressaoRepository.listarDoUsuarioNosSegmentos(
        usuarioId,
        segmentos.stream()
            .map(SegmentoRecorrencia::getId)
            .collect(java.util.stream.Collectors.toSet()));
  }

  private Set<UUID> idsSegmentos(List<SegmentoRecorrencia> segmentos) {
    return segmentos.stream()
        .map(SegmentoRecorrencia::getId)
        .collect(java.util.stream.Collectors.toSet());
  }

  private List<Transacao> unirTransacoesRecorrentes(
      List<Transacao> porDataFinanceira, List<Transacao> porSegmento) {
    var ids = new LinkedHashSet<UUID>();
    var resultado = new ArrayList<Transacao>();
    for (var transacao : porDataFinanceira) {
      if (ids.add(transacao.getId())) {
        resultado.add(transacao);
      }
    }
    for (var transacao : porSegmento) {
      if (ids.add(transacao.getId())) {
        resultado.add(transacao);
      }
    }
    return resultado;
  }

  private boolean atendeFiltros(
      ResolverOcorrenciasRecorrentes.Resultado resultado,
      Set<UUID> contaIds,
      Set<UUID> categoriaIds) {
    return (contaIds.isEmpty() || contaIds.contains(resultado.contaId()))
        && (categoriaIds.isEmpty() || categoriaIds.contains(resultado.categoriaId()));
  }

  private Item item(ResolverOcorrenciasRecorrentes.Resultado resultado) {
    return new Item(
        resultado.origem(),
        resultado.operacaoId(),
        resultado.situacao(),
        resultado.descricao(),
        resultado.observacoes(),
        resultado.valor(),
        resultado.dataFinanceira(),
        resultado.efetivadoEm(),
        resultado.contaId(),
        resultado.categoriaId(),
        resultado.contaContraparteId(),
        resultado.grupoRecorrenciaId(),
        resultado.segmentoRecorrenciaId(),
        resultado.dataOriginalRecorrencia(),
        resultado.numeroParcela(),
        resultado.rrule(),
        resultado.inicioRecorrencia(),
        resultado.politicaDataOcorrencia());
  }

  private Periodo resolverPeriodo(YearMonth inicio, YearMonth fim) {
    if (inicio == null || fim == null) {
      throw new ValidacaoException("periodo", "Inicio e fim devem ser informados juntos");
    }

    if (inicio.isAfter(fim)) {
      throw new ValidacaoException("periodo", "Inicio deve ser anterior ou igual ao fim");
    }

    if (ChronoUnit.MONTHS.between(inicio, fim) + 1 > MAXIMO_MESES) {
      throw new ValidacaoException("periodo", "O periodo deve ter no maximo 12 meses");
    }

    return new Periodo(inicio, fim);
  }

  private <T> Set<T> conjunto(List<T> valores) {
    return valores == null ? Set.of() : new LinkedHashSet<>(valores);
  }

  private BigDecimal monetario(BigDecimal valor) {
    return valor.setScale(ESCALA_MONETARIA, RoundingMode.UNNECESSARY);
  }

  private BigDecimal impacto(TipoTransacao tipo, BigDecimal valor) {
    return tipo == TipoTransacao.RECEITA ? valor : valor.negate();
  }

  private RecursoNaoEncontradoException recursoNaoEncontrado() {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Recurso nao encontrado");
  }

  private record Periodo(YearMonth inicio, YearMonth fim) {
    private LocalDate primeiroDia() {
      return inicio.atDay(1);
    }

    private LocalDate ultimoDia() {
      return fim.atEndOfMonth();
    }

    private LocalDate dataSaldoAbertura() {
      return primeiroDia().minusDays(1);
    }
  }

  public record Comando(
      UUID usuarioId,
      YearMonth inicio,
      YearMonth fim,
      List<UUID> contaIds,
      List<UUID> categoriaIds) {}

  public record Resultado(
      YearMonth inicio, YearMonth fim, BigDecimal saldoAbertura, List<Item> itens) {}

  public record Item(
      OrigemItemConsulta origem,
      UUID operacaoId,
      SituacaoTransacao situacao,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      java.time.Instant efetivadoEm,
      UUID contaId,
      UUID categoriaId,
      UUID contaContraparteId,
      UUID grupoRecorrenciaId,
      UUID segmentoRecorrenciaId,
      LocalDate dataOriginalRecorrencia,
      Integer numeroParcela,
      String rrule,
      LocalDate inicioRecorrencia,
      PoliticaDataOcorrencia politicaDataOcorrencia) {
    public Item(
        OrigemItemConsulta origem,
        UUID operacaoId,
        SituacaoTransacao situacao,
        String descricao,
        String observacoes,
        BigDecimal valor,
        LocalDate dataFinanceira,
        java.time.Instant efetivadoEm,
        UUID contaId,
        UUID categoriaId,
        UUID contaContraparteId) {
      this(
          origem,
          operacaoId,
          situacao,
          descricao,
          observacoes,
          valor,
          dataFinanceira,
          efetivadoEm,
          contaId,
          categoriaId,
          contaContraparteId,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }
  }
}
