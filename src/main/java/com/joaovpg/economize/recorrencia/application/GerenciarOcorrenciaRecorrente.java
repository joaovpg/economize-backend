package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.ExpansorRecorrencia;
import com.joaovpg.economize.recorrencia.FormatadorDescricaoParcela;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.LeitorRruleRecorrencia;
import com.joaovpg.economize.recorrencia.OcorrenciaRecorrencia;
import com.joaovpg.economize.recorrencia.RegraRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SupressaoRecorrencia;
import com.joaovpg.economize.recorrencia.SupressaoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.enums.EscopoOcorrencia;
import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.enums.TipoGrupoRecorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.usuario.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class GerenciarOcorrenciaRecorrente {
  private final SegmentoRecorrenciaRepository segmentoRepository;
  private final GrupoRecorrenciaRepository grupoRepository;
  private final TransacaoRepository transacaoRepository;
  private final SupressaoRecorrenciaRepository supressaoRepository;
  private final ContaFinanceiraRepository contaRepository;
  private final CategoriaRepository categoriaRepository;
  private final ExpansorRecorrencia expansor;
  private final LeitorRruleRecorrencia leitorRrule;

  public GerenciarOcorrenciaRecorrente(
      SegmentoRecorrenciaRepository segmentoRepository,
      GrupoRecorrenciaRepository grupoRepository,
      TransacaoRepository transacaoRepository,
      SupressaoRecorrenciaRepository supressaoRepository,
      ContaFinanceiraRepository contaRepository,
      CategoriaRepository categoriaRepository,
      ExpansorRecorrencia expansor,
      LeitorRruleRecorrencia leitorRrule) {
    this.segmentoRepository = segmentoRepository;
    this.grupoRepository = grupoRepository;
    this.transacaoRepository = transacaoRepository;
    this.supressaoRepository = supressaoRepository;
    this.contaRepository = contaRepository;
    this.categoriaRepository = categoriaRepository;
    this.expansor = expansor;
    this.leitorRrule = leitorRrule;
  }

  @Transactional
  public Resultado editar(Comando comando) {
    return editar(comando, null);
  }

  @Transactional
  public Resultado editar(Comando comando, TipoGrupoRecorrencia tipoEsperado) {
    var segmento = carregarSegmento(comando.usuarioId(), comando.segmentoId(), tipoEsperado);
    var ocorrencia = validarOcorrencia(segmento, comando.dataOriginal());
    var transacao =
        transacaoRepository.buscarOcorrenciaDoUsuarioParaEdicao(
            segmento.getId(), comando.dataOriginal(), comando.usuarioId());
    garantirGrupoAtivoParaNovaOcorrencia(segmento, transacao.isPresent());

    if (comando.quantidadeTotalOriginal() != null
        && segmento.getGrupo().getTipo() != TipoGrupoRecorrencia.PARCELAMENTO) {
      throw new RegraNegocioException(
          "QUANTIDADE_PARCELAS_INVALIDA", "A quantidade total original so vale para parcelamentos");
    }

    if (comando.escopo() == EscopoOcorrencia.ONLY_THIS) {
      if (comando.quantidadeTotalOriginal() != null) {
        throw new RegraNegocioException(
            "QUANTIDADE_PARCELAS_INVALIDA", "Alterar a quantidade exige o escopo THIS_AND_FUTURE");
      }
      return editarSomenteEsta(segmento, ocorrencia, transacao.orElse(null), comando);
    }
    if (comando.escopo() == EscopoOcorrencia.THIS_AND_FUTURE) {
      if (transacao.isPresent()) {
        throw new RegraNegocioException(
            "THIS_AND_FUTURE_EXIGE_VIRTUAL",
            "THIS_AND_FUTURE so pode ser usado em ocorrencia virtual");
      }
      if (supressaoRepository.existe(
          segmento.getId(), comando.dataOriginal(), comando.usuarioId())) {
        throw new RegraNegocioException(
            "OCORRENCIA_CANCELADA", "A ocorrencia selecionada esta cancelada");
      }
      if (comando.quantidadeTotalOriginal() != null) {
        return alterarQuantidadeParcelamento(segmento, ocorrencia, comando);
      }
      return editarEstaEAsProximas(segmento, ocorrencia, comando);
    }
    throw new RegraNegocioException("ESCOPO_OCORRENCIA_INVALIDO", "Escopo de ocorrencia invalido");
  }

  @Transactional
  public Resultado efetivar(EfetivarComando comando) {
    return efetivar(comando, null);
  }

  @Transactional
  public Resultado efetivar(EfetivarComando comando, TipoGrupoRecorrencia tipoEsperado) {
    var segmento = carregarSegmento(comando.usuarioId(), comando.segmentoId(), tipoEsperado);
    var ocorrencia = validarOcorrencia(segmento, comando.dataOriginal());
    var transacao =
        transacaoRepository.buscarOcorrenciaDoUsuarioParaEdicao(
            segmento.getId(), comando.dataOriginal(), comando.usuarioId());
    garantirGrupoAtivoParaNovaOcorrencia(segmento, transacao.isPresent());
    if (transacao.isEmpty()
        && supressaoRepository.existe(
            segmento.getId(), comando.dataOriginal(), comando.usuarioId())) {
      throw new RegraNegocioException("OCORRENCIA_CANCELADA", "A ocorrencia esta cancelada");
    }

    var atual = transacao.orElseGet(() -> novaTransacao(segmento, comando.dataOriginal()));
    var dataFinanceira =
        comando.dataFinanceira() != null
            ? comando.dataFinanceira()
            : atual.getDataFinanceira() == null
                ? ocorrencia.dataOriginal()
                : atual.getDataFinanceira();
    if (atual.getSituacao() == SituacaoTransacao.EFETIVADA) {
      return Resultado.de(atual, segmento, numeroParcela(segmento, ocorrencia));
    }
    validarData(atual.getUsuario(), atual.getConta(), dataFinanceira, true);
    atual.setDataFinanceira(dataFinanceira);
    atual.setSituacao(SituacaoTransacao.EFETIVADA);
    atual.setEfetivadoEm(Instant.now().truncatedTo(ChronoUnit.MICROS));
    if (atual.getId() == null) {
      transacaoRepository.persist(atual);
    }
    return Resultado.de(atual, segmento, numeroParcela(segmento, ocorrencia));
  }

  @Transactional
  public void excluir(
      UUID usuarioId, UUID segmentoId, LocalDate dataOriginal, EscopoOcorrencia escopo) {
    excluir(usuarioId, segmentoId, dataOriginal, escopo, null);
  }

  @Transactional
  public void excluir(
      UUID usuarioId, UUID segmentoId, LocalDate dataOriginal, TipoGrupoRecorrencia tipoEsperado) {
    excluir(usuarioId, segmentoId, dataOriginal, EscopoOcorrencia.ONLY_THIS, tipoEsperado);
  }

  @Transactional
  public void excluir(
      UUID usuarioId,
      UUID segmentoId,
      LocalDate dataOriginal,
      EscopoOcorrencia escopo,
      TipoGrupoRecorrencia tipoEsperado) {
    var segmento = carregarSegmento(usuarioId, segmentoId, tipoEsperado);
    var transacao =
        transacaoRepository.buscarOcorrenciaDoUsuarioParaEdicao(
            segmentoId, dataOriginal, usuarioId);
    garantirGrupoAtivoParaNovaOcorrencia(segmento, transacao.isPresent());

    if (escopo == EscopoOcorrencia.THIS_AND_FUTURE) {
      if (transacao.isPresent()) {
        throw new RegraNegocioException(
            "THIS_AND_FUTURE_EXIGE_VIRTUAL",
            "THIS_AND_FUTURE so pode ser usado em ocorrencia virtual");
      }
      validarOcorrencia(segmento, dataOriginal);
      excluirEstaEAsProximas(segmento, dataOriginal, usuarioId);
      return;
    }

    if (escopo != EscopoOcorrencia.ONLY_THIS) {
      throw new RegraNegocioException(
          "ESCOPO_OCORRENCIA_INVALIDO", "Escopo de ocorrencia invalido");
    }
    if (transacao.isEmpty()) {
      validarOcorrencia(segmento, dataOriginal);
    }
    transacao.ifPresent(transacaoRepository::delete);
    if (supressaoRepository.buscarDoUsuario(segmentoId, dataOriginal, usuarioId).isEmpty()) {
      var supressao = new SupressaoRecorrencia();
      supressao.setUsuario(segmento.getUsuario());
      supressao.setGrupo(segmento.getGrupo());
      supressao.setSegmento(segmento);
      supressao.setIdentificadorRecorrencia(dataOriginal);
      supressaoRepository.persist(supressao);
    }
  }

  private void excluirEstaEAsProximas(
      SegmentoRecorrencia segmento, LocalDate dataOriginal, UUID usuarioId) {
    removerMaterializacoesPlanejadas(segmento, dataOriginal, usuarioId);
    if (dataOriginal.isEqual(segmento.getInicio())) {
      segmento.setStatus(StatusRecorrencia.CANCELADO);
    } else {
      segmento.setFim(dataOriginal.minusDays(1));
      segmento.setStatus(StatusRecorrencia.CONCLUIDO);
    }

    var segmentosFuturos =
        segmentoRepository.listarAtivosDoGrupoAPartirDe(
            segmento.getGrupo().getId(), segmento.getId(), usuarioId, dataOriginal);
    for (var segmentoFuturo : segmentosFuturos) {
      removerMaterializacoesPlanejadas(segmentoFuturo, segmentoFuturo.getInicio(), usuarioId);
      segmentoFuturo.setStatus(StatusRecorrencia.CANCELADO);
    }

    if (!segmentoRepository.existeSegmentoAtivoDoGrupo(segmento.getGrupo().getId(), usuarioId)) {
      var possuiHistoricoVirtual =
          dataOriginal.isAfter(segmento.getInicio())
              || segmentoRepository.existeSegmentoAnteriorDoGrupo(
                  segmento.getGrupo().getId(), usuarioId, segmento.getInicio());
      segmento
          .getGrupo()
          .setStatus(
              possuiHistoricoVirtual ? StatusRecorrencia.CONCLUIDO : StatusRecorrencia.CANCELADO);
    }
  }

  private void removerMaterializacoesPlanejadas(
      SegmentoRecorrencia segmento, LocalDate inicio, UUID usuarioId) {
    transacaoRepository
        .listarOcorrenciasDoSegmentoAPartirDe(segmento.getId(), inicio, usuarioId)
        .stream()
        .filter(transacao -> transacao.getSituacao() == SituacaoTransacao.PLANEJADA)
        .forEach(transacaoRepository::delete);
  }

  private Resultado editarSomenteEsta(
      SegmentoRecorrencia segmento,
      OcorrenciaRecorrencia ocorrencia,
      Transacao transacao,
      Comando comando) {
    var conta = resolverConta(segmento, comando.contaId(), comando.usuarioId());
    var categoria = resolverCategoria(segmento, comando.categoriaId(), comando.usuarioId());
    RecorrenciaValidacao.dadosFinanceiros(
        comando.tipo(),
        comando.descricao(),
        comando.observacoes(),
        comando.valor(),
        comando.dataFinanceira());
    validarData(
        segmento.getUsuario(),
        conta,
        comando.dataFinanceira(),
        transacao != null && transacao.getSituacao() == SituacaoTransacao.EFETIVADA);
    if (transacao == null) {
      removerSupressao(segmento, comando.dataOriginal(), comando.usuarioId());
      transacao = novaTransacao(segmento, comando.dataOriginal());
      transacaoRepository.persist(transacao);
    }
    var situacao = transacao.getSituacao();
    var efetivadoEm = transacao.getEfetivadoEm();
    transacao.setConta(conta);
    transacao.setCategoria(categoria);
    transacao.setTipo(comando.tipo());
    transacao.setDescricao(comando.descricao().strip());
    transacao.setObservacoes(comando.observacoes() == null ? null : comando.observacoes().strip());
    transacao.setValor(comando.valor());
    transacao.setDataFinanceira(comando.dataFinanceira());
    transacao.setSituacao(situacao);
    transacao.setEfetivadoEm(efetivadoEm);
    transacao.setExcecaoRecorrencia(true);
    transacao.setGrupoRecorrencia(segmento.getGrupo());
    transacao.setSegmentoRecorrencia(segmento);
    transacao.setIdentificadorRecorrencia(ocorrencia.dataOriginal());
    return Resultado.de(transacao, segmento, numeroParcela(segmento, ocorrencia));
  }

  private Resultado editarEstaEAsProximas(
      SegmentoRecorrencia segmento, OcorrenciaRecorrencia ocorrencia, Comando comando) {
    var dataInicioNova = comando.dataFinanceira();
    if (dataInicioNova.isBefore(comando.dataOriginal())) {
      throw new RegraNegocioException(
          "DATA_INICIO_FUTURO_INVALIDA",
          "A nova regra deve iniciar na data da ocorrencia ou depois dela");
    }
    var regraAnterior = leitorRrule.lerSegmento(segmento.getInicio(), segmento.getRrule());
    if (segmentoRepository.existeSegmentoPosteriorAtivo(
        segmento.getGrupo().getId(),
        segmento.getId(),
        comando.usuarioId(),
        comando.dataOriginal())) {
      throw new RegraNegocioException(
          "SEGMENTO_NAO_ATUAL", "A edicao deve partir do segmento mais recente do grupo");
    }
    var regraNova = criarRegraNova(segmento, ocorrencia, regraAnterior, dataInicioNova, comando);
    var transacoesFuturas =
        transacaoRepository.listarOcorrenciasDoSegmentoAPartirDe(
            segmento.getId(), comando.dataOriginal(), comando.usuarioId());
    var efetivadas =
        transacoesFuturas.stream()
            .filter(transacao -> transacao.getSituacao() == SituacaoTransacao.EFETIVADA)
            .toList();
    transacoesFuturas.stream()
        .filter(transacao -> transacao.getSituacao() == SituacaoTransacao.PLANEJADA)
        .forEach(transacaoRepository::delete);

    if (comando.dataOriginal().isEqual(segmento.getInicio())) {
      segmento.setStatus(StatusRecorrencia.CANCELADO);
    } else {
      segmento.setFim(comando.dataOriginal().minusDays(1));
      segmento.setStatus(StatusRecorrencia.CONCLUIDO);
    }

    var conta = resolverConta(segmento, comando.contaId(), comando.usuarioId());
    var categoria = resolverCategoria(segmento, comando.categoriaId(), comando.usuarioId());
    RecorrenciaValidacao.dadosFinanceiros(
        comando.tipo(),
        comando.descricao(),
        comando.observacoes(),
        comando.valor(),
        dataInicioNova);
    validarData(segmento.getUsuario(), conta, dataInicioNova, false);

    var novoSegmento = new SegmentoRecorrencia();
    novoSegmento.setUsuario(segmento.getUsuario());
    novoSegmento.setGrupo(segmento.getGrupo());
    novoSegmento.setConta(conta);
    novoSegmento.setCategoria(categoria);
    novoSegmento.setTipo(comando.tipo());
    novoSegmento.setDescricao(comando.descricao().strip());
    novoSegmento.setObservacoes(
        comando.observacoes() == null ? null : comando.observacoes().strip());
    novoSegmento.setValor(comando.valor());
    novoSegmento.setInicio(dataInicioNova);
    novoSegmento.setFim(regraNova.ate());
    novoSegmento.setRrule(regraNova.rrule());
    novoSegmento.setTotalOcorrencias(regraNova.quantidadeOcorrencias());
    if (segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.PARCELAMENTO) {
      var primeiraParcela = numeroParcela(segmento, ocorrencia);
      novoSegmento.setNumeroPrimeiraParcela(primeiraParcela);
      novoSegmento.setQuantidadeTotalOriginal(segmento.getQuantidadeTotalOriginal());
    }
    novoSegmento.setStatus(StatusRecorrencia.ATIVO);
    segmentoRepository.persist(novoSegmento);

    for (var efetivada : efetivadas) {
      if (ehOcorrencia(novoSegmento, efetivada.getIdentificadorRecorrencia())) {
        criarSupressao(novoSegmento, efetivada.getIdentificadorRecorrencia());
      }
    }
    return Resultado.deSegmento(novoSegmento);
  }

  private Resultado alterarQuantidadeParcelamento(
      SegmentoRecorrencia segmento, OcorrenciaRecorrencia ocorrencia, Comando comando) {
    var primeiraParcela = numeroParcela(segmento, ocorrencia);
    var novaQuantidade = comando.quantidadeTotalOriginal();
    if (novaQuantidade < primeiraParcela) {
      throw new RegraNegocioException(
          "QUANTIDADE_PARCELAS_INVALIDA", "A nova quantidade deve incluir a parcela selecionada");
    }

    var dataInicioNova = comando.dataFinanceira();
    if (dataInicioNova.isBefore(comando.dataOriginal())) {
      throw new RegraNegocioException(
          "DATA_INICIO_FUTURO_INVALIDA",
          "A nova regra deve iniciar na data da ocorrencia ou depois dela");
    }

    var regraAnterior = leitorRrule.lerSegmento(segmento.getInicio(), segmento.getRrule());
    var frequencia =
        comando.frequencia() == null ? regraAnterior.frequencia() : comando.frequencia();
    var intervalo = comando.intervalo() == null ? regraAnterior.intervalo() : comando.intervalo();
    var regraNova =
        RecorrenciaValidacao.regra(
            dataInicioNova,
            frequencia,
            intervalo,
            frequencia == FrequenciaRecorrencia.WEEKLY
                ? Set.of(dataInicioNova.getDayOfWeek())
                : Set.of(),
            Set.of(),
            novaQuantidade - primeiraParcela + 1,
            null);

    var transacoesFuturas =
        transacaoRepository.listarOcorrenciasDoSegmentoAPartirDe(
            segmento.getId(), comando.dataOriginal(), comando.usuarioId());
    var efetivadas =
        transacoesFuturas.stream()
            .filter(transacao -> transacao.getSituacao() == SituacaoTransacao.EFETIVADA)
            .toList();
    transacoesFuturas.stream()
        .filter(transacao -> transacao.getSituacao() == SituacaoTransacao.PLANEJADA)
        .forEach(transacaoRepository::delete);

    encerrarSegmento(segmento, comando.dataOriginal());
    segmento.getGrupo().setStatus(StatusRecorrencia.CONCLUIDO);

    var conta = resolverConta(segmento, comando.contaId(), comando.usuarioId(), dataInicioNova);
    var categoria = resolverCategoria(segmento, comando.categoriaId(), comando.usuarioId());
    RecorrenciaValidacao.dadosFinanceiros(
        comando.tipo(),
        comando.descricao(),
        comando.observacoes(),
        comando.valor(),
        dataInicioNova);
    validarData(segmento.getUsuario(), conta, dataInicioNova, false);

    var grupo = new GrupoRecorrencia();
    grupo.setUsuario(segmento.getUsuario());
    grupo.setDescricao(comando.descricao().strip());
    grupo.setTipo(TipoGrupoRecorrencia.PARCELAMENTO);
    grupo.setStatus(StatusRecorrencia.ATIVO);
    grupoRepository.persist(grupo);

    var novoSegmento = new SegmentoRecorrencia();
    novoSegmento.setUsuario(segmento.getUsuario());
    novoSegmento.setGrupo(grupo);
    novoSegmento.setConta(conta);
    novoSegmento.setCategoria(categoria);
    novoSegmento.setTipo(comando.tipo());
    novoSegmento.setDescricao(comando.descricao().strip());
    novoSegmento.setObservacoes(
        comando.observacoes() == null ? null : comando.observacoes().strip());
    novoSegmento.setValor(comando.valor());
    novoSegmento.setInicio(dataInicioNova);
    novoSegmento.setRrule(regraNova.rrule());
    novoSegmento.setTotalOcorrencias(regraNova.quantidadeOcorrencias());
    novoSegmento.setNumeroPrimeiraParcela(primeiraParcela);
    novoSegmento.setQuantidadeTotalOriginal(novaQuantidade);
    novoSegmento.setStatus(StatusRecorrencia.ATIVO);
    segmentoRepository.persist(novoSegmento);

    for (var efetivada : efetivadas) {
      if (ehOcorrencia(novoSegmento, efetivada.getIdentificadorRecorrencia())) {
        criarSupressao(novoSegmento, efetivada.getIdentificadorRecorrencia());
      }
    }
    return Resultado.deSegmento(novoSegmento);
  }

  private void encerrarSegmento(SegmentoRecorrencia segmento, LocalDate dataOriginal) {
    if (dataOriginal.isEqual(segmento.getInicio())) {
      segmento.setStatus(StatusRecorrencia.CANCELADO);
    } else {
      segmento.setFim(dataOriginal.minusDays(1));
      segmento.setStatus(StatusRecorrencia.CONCLUIDO);
    }
  }

  private RegraRecorrencia criarRegraNova(
      SegmentoRecorrencia segmento,
      OcorrenciaRecorrencia ocorrencia,
      RegraRecorrencia anterior,
      LocalDate inicio,
      Comando comando) {
    validarTermino(comando);
    var frequencia = comando.frequencia() == null ? anterior.frequencia() : comando.frequencia();
    var intervalo = comando.intervalo() == null ? anterior.intervalo() : comando.intervalo();
    var diasSemana = comando.diasSemana() == null ? anterior.diasSemana() : comando.diasSemana();
    var diasMes = comando.diasMes() == null ? anterior.diasMes() : comando.diasMes();
    Integer quantidade;
    LocalDate ate;
    if (segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.PARCELAMENTO) {
      var numero = numeroParcela(segmento, ocorrencia);
      quantidade = segmento.getQuantidadeTotalOriginal() - numero + 1;
      ate = null;
      if (frequencia == FrequenciaRecorrencia.WEEKLY) {
        diasSemana = Set.of(inicio.getDayOfWeek());
        diasMes = Set.of();
      } else if (frequencia == FrequenciaRecorrencia.MONTHLY
          || frequencia == FrequenciaRecorrencia.YEARLY) {
        diasSemana = Set.of();
        diasMes = Set.of();
      } else {
        diasSemana = Set.of();
        diasMes = Set.of();
      }
    } else {
      var alterouTermino =
          comando.quantidadeOcorrencias() != null
              || comando.ate() != null
              || Boolean.TRUE.equals(comando.semTermino());
      quantidade =
          comando.quantidadeOcorrencias() != null
              ? comando.quantidadeOcorrencias()
              : comando.ate() != null || Boolean.TRUE.equals(comando.semTermino())
                  ? null
                  : anterior.quantidadeOcorrencias() == null
                      ? null
                      : anterior.quantidadeOcorrencias() - ocorrencia.numeroOcorrencia() + 1;
      ate = comando.ate() != null ? comando.ate() : alterouTermino ? null : anterior.ate();
      if (quantidade != null) {
        ate = null;
      }
    }
    var alterouPadrao =
        comando.frequencia() != null
            || comando.intervalo() != null
            || comando.diasSemana() != null
            || comando.diasMes() != null;
    if (segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.RECORRENCIA && alterouPadrao) {
      return RecorrenciaValidacao.regraRecorrencia(
          inicio, frequencia, intervalo, diasSemana, diasMes, quantidade, ate);
    }
    if (!alterouPadrao) {
      try {
        return RegraRecorrencia.paraSegmento(
            inicio, frequencia, intervalo, diasSemana, diasMes, quantidade, ate);
      } catch (IllegalArgumentException exception) {
        throw new RegraNegocioException("RRULE_INVALIDA", exception.getMessage());
      }
    }
    return RecorrenciaValidacao.regra(
        inicio, frequencia, intervalo, diasSemana, diasMes, quantidade, ate);
  }

  private void validarTermino(Comando comando) {
    var quantidadeInformada = comando.quantidadeOcorrencias() != null;
    var dataInformada = comando.ate() != null;
    var semTermino = Boolean.TRUE.equals(comando.semTermino());
    if ((quantidadeInformada && dataInformada)
        || (semTermino && (quantidadeInformada || dataInformada))) {
      throw new RegraNegocioException(
          "TERMINO_INVALIDO", "Informe somente quantidade, data limite ou sem termino");
    }
  }

  private SegmentoRecorrencia carregarSegmento(
      UUID usuarioId, UUID segmentoId, TipoGrupoRecorrencia tipoEsperado) {
    var segmento =
        segmentoRepository
            .buscarDoUsuarioParaEdicao(segmentoId, usuarioId)
            .orElseThrow(() -> RecorrenciaValidacao.naoEncontrado("Recorrencia"));
    if (tipoEsperado != null && segmento.getGrupo().getTipo() != tipoEsperado) {
      throw RecorrenciaValidacao.naoEncontrado("Recorrencia");
    }
    return segmento;
  }

  private void garantirGrupoAtivoParaNovaOcorrencia(
      SegmentoRecorrencia segmento, boolean possuiTransacaoMaterializada) {
    if (!possuiTransacaoMaterializada
        && segmento.getGrupo().getStatus() == StatusRecorrencia.CANCELADO) {
      throw new RecursoNaoEncontradoException(
          "RECURSO_NAO_ENCONTRADO", "Ocorrencia nao encontrada");
    }
  }

  private OcorrenciaRecorrencia validarOcorrencia(
      SegmentoRecorrencia segmento, LocalDate dataOriginal) {
    if (dataOriginal == null) {
      throw new RegraNegocioException(
          "DATA_ORIGINAL_OBRIGATORIA", "Data original da ocorrencia obrigatoria");
    }
    var regra = leitorRrule.lerSegmento(segmento.getInicio(), segmento.getRrule());
      if (segmento.getFim() != null && segmento.getFim().isBefore(dataOriginal)) {
      throw new RecursoNaoEncontradoException(
          "RECURSO_NAO_ENCONTRADO", "Ocorrencia nao encontrada");
    }
    var politica = politica(segmento);
    return expansor.expandir(regra, segmento.getInicio(), dataOriginal, politica).stream()
        .filter(ocorrencia -> ocorrencia.dataOriginal().equals(dataOriginal))
        .findFirst()
        .orElseThrow(
            () ->
                new RecursoNaoEncontradoException(
                    "RECURSO_NAO_ENCONTRADO", "Ocorrencia nao encontrada"));
  }

  private boolean ehOcorrencia(SegmentoRecorrencia segmento, LocalDate data) {
    try {
      validarOcorrencia(segmento, data);
      return true;
    } catch (RecursoNaoEncontradoException exception) {
      return false;
    }
  }

  private ContaFinanceira resolverConta(
      SegmentoRecorrencia segmento, UUID contaId, UUID usuarioId) {
    return resolverConta(segmento, contaId, usuarioId, segmento.getInicio());
  }

  private ContaFinanceira resolverConta(
      SegmentoRecorrencia segmento, UUID contaId, UUID usuarioId, LocalDate inicio) {
    if (contaId != null && contaId.equals(segmento.getConta().getId())) {
      return segmento.getConta();
    }
    if (contaId == null) {
      throw new RegraNegocioException("CONTA_OBRIGATORIA", "Conta obrigatoria");
    }
    return RecorrenciaValidacao.conta(contaRepository, contaId, usuarioId, inicio);
  }

  private Categoria resolverCategoria(
      SegmentoRecorrencia segmento, UUID categoriaId, UUID usuarioId) {
    if (categoriaId == null) {
      return null;
    }
    if (segmento.getCategoria() != null && categoriaId.equals(segmento.getCategoria().getId())) {
      return segmento.getCategoria();
    }
    return RecorrenciaValidacao.categoria(categoriaRepository, categoriaId, usuarioId);
  }

  private void validarData(
      Usuario usuario, ContaFinanceira conta, LocalDate data, boolean exigeDataNaoFutura) {
    if (data == null) {
      throw new RegraNegocioException("DATA_FINANCEIRA_OBRIGATORIA", "Data financeira obrigatoria");
    }
    if (data.isBefore(conta.getDataSaldoInicial())) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL",
          "A data financeira nao pode ser anterior a data do saldo inicial da conta");
    }
    if (exigeDataNaoFutura && data.isAfter(LocalDate.now(ZoneId.of(usuario.getTimezone())))) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_FUTURA", "Uma transacao efetivada nao pode ter data financeira futura");
    }
  }

  private Transacao novaTransacao(SegmentoRecorrencia segmento, LocalDate dataOriginal) {
    var transacao = new Transacao();
    transacao.setUsuario(segmento.getUsuario());
    transacao.setConta(segmento.getConta());
    transacao.setCategoria(segmento.getCategoria());
    transacao.setGrupoRecorrencia(segmento.getGrupo());
    transacao.setSegmentoRecorrencia(segmento);
    transacao.setTipo(segmento.getTipo());
    transacao.setSituacao(SituacaoTransacao.PLANEJADA);
    transacao.setDescricao(segmento.getDescricao());
    transacao.setObservacoes(segmento.getObservacoes());
    transacao.setValor(segmento.getValor());
    transacao.setDataFinanceira(dataOriginal);
    transacao.setIdentificadorRecorrencia(dataOriginal);
    transacao.setExcecaoRecorrencia(true);
    return transacao;
  }

  private void removerSupressao(
      SegmentoRecorrencia segmento, LocalDate dataOriginal, UUID usuarioId) {
    supressaoRepository
        .buscarDoUsuarioParaEdicao(segmento.getId(), dataOriginal, usuarioId)
        .ifPresent(supressaoRepository::delete);
  }

  private void criarSupressao(SegmentoRecorrencia segmento, LocalDate dataOriginal) {
    if (supressaoRepository.existe(segmento.getId(), dataOriginal, segmento.getUsuario().getId())) {
      return;
    }
    var supressao = new SupressaoRecorrencia();
    supressao.setUsuario(segmento.getUsuario());
    supressao.setGrupo(segmento.getGrupo());
    supressao.setSegmento(segmento);
    supressao.setIdentificadorRecorrencia(dataOriginal);
    supressaoRepository.persist(supressao);
  }

  private PoliticaDataOcorrencia politica(SegmentoRecorrencia segmento) {
    return segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.PARCELAMENTO
        ? PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
        : PoliticaDataOcorrencia.PADRAO;
  }

  private int numeroParcela(SegmentoRecorrencia segmento, OcorrenciaRecorrencia ocorrencia) {
    return segmento.getNumeroPrimeiraParcela() == null
        ? 0
        : segmento.getNumeroPrimeiraParcela() + ocorrencia.numeroOcorrencia() - 1;
  }

  public record Comando(
      UUID usuarioId,
      UUID segmentoId,
      LocalDate dataOriginal,
      EscopoOcorrencia escopo,
      UUID contaId,
      UUID categoriaId,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      FrequenciaRecorrencia frequencia,
      Integer intervalo,
      Set<DayOfWeek> diasSemana,
      Set<Integer> diasMes,
      Integer quantidadeOcorrencias,
      LocalDate ate,
      Boolean semTermino,
      Integer quantidadeTotalOriginal) {}

  public record EfetivarComando(
      UUID usuarioId, UUID segmentoId, LocalDate dataOriginal, LocalDate dataFinanceira) {}

  public record Resultado(
      UUID transacaoId,
      UUID grupoId,
      UUID segmentoId,
      TipoGrupoRecorrencia tipoGrupo,
      StatusRecorrencia status,
      TipoTransacao tipo,
      SituacaoTransacao situacao,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      Instant efetivadoEm,
      UUID contaId,
      UUID categoriaId,
      LocalDate dataOriginalRecorrencia,
      Integer numeroParcela,
      String rrule,
      LocalDate inicioRecorrencia,
      PoliticaDataOcorrencia politicaDataOcorrencia) {
    static Resultado de(Transacao transacao, SegmentoRecorrencia segmento, int numeroParcela) {
      var numero = numeroParcela == 0 ? null : numeroParcela;
      return new Resultado(
          transacao.getId(),
          segmento.getGrupo().getId(),
          segmento.getId(),
          segmento.getGrupo().getTipo(),
          segmento.getStatus(),
          transacao.getTipo(),
          transacao.getSituacao(),
          FormatadorDescricaoParcela.formatar(
              transacao.getDescricao(),
              segmento.getGrupo().getTipo(),
              numero,
              segmento.getQuantidadeTotalOriginal()),
          transacao.getObservacoes(),
          transacao.getValor(),
          transacao.getDataFinanceira(),
          transacao.getEfetivadoEm(),
          transacao.getConta().getId(),
          transacao.getCategoria() == null ? null : transacao.getCategoria().getId(),
          transacao.getIdentificadorRecorrencia(),
          numero,
          segmento.getRrule(),
          segmento.getInicio(),
          segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.PARCELAMENTO
              ? PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
              : PoliticaDataOcorrencia.PADRAO);
    }

    static Resultado deSegmento(SegmentoRecorrencia segmento) {
      var numero = segmento.getNumeroPrimeiraParcela();
      return new Resultado(
          null,
          segmento.getGrupo().getId(),
          segmento.getId(),
          segmento.getGrupo().getTipo(),
          segmento.getStatus(),
          segmento.getTipo(),
          null,
          FormatadorDescricaoParcela.formatar(
              segmento.getDescricao(),
              segmento.getGrupo().getTipo(),
              numero,
              segmento.getQuantidadeTotalOriginal()),
          segmento.getObservacoes(),
          segmento.getValor(),
          segmento.getInicio(),
          null,
          segmento.getConta().getId(),
          segmento.getCategoria() == null ? null : segmento.getCategoria().getId(),
          segmento.getInicio(),
          numero,
          segmento.getRrule(),
          segmento.getInicio(),
          segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.PARCELAMENTO
              ? PoliticaDataOcorrencia.AJUSTAR_ULTIMO_DIA_MES
              : PoliticaDataOcorrencia.PADRAO);
    }
  }
}
