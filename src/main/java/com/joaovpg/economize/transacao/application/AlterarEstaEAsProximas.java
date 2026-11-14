package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.application.LocalizarOcorrenciaRecorrente;
import com.joaovpg.economize.recorrencia.application.RepeticaoValidacao;
import com.joaovpg.economize.recorrencia.domain.ExpansorRecorrencia;
import com.joaovpg.economize.recorrencia.domain.FrequenciaRecorrencia;
import com.joaovpg.economize.recorrencia.domain.LeitorRruleRecorrencia;
import com.joaovpg.economize.recorrencia.domain.OcorrenciaRecorrencia;
import com.joaovpg.economize.recorrencia.domain.PoliticaDataOcorrencia;
import com.joaovpg.economize.recorrencia.domain.RegraRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.domain.StatusRecorrencia;
import com.joaovpg.economize.recorrencia.domain.TipoGrupoRecorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.shared.exception.ValidacaoException;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.transacao.domain.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.domain.ResolucaoSobreposicao;
import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
import com.joaovpg.economize.usuario.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Altera uma ocorrência de série e as seguintes (escopo ESTA_E_AS_PROXIMAS). O corte usa a data
 * original; protegidas do grupo com data original igual ou posterior ao corte são preservadas. O
 * destino pode ser transação simples, que encerra a projeção, ou um novo trecho, que reaproveita o
 * grupo quando a modalidade e o total não mudam e cria outro grupo caso contrário.
 */
@ApplicationScoped
public class AlterarEstaEAsProximas {
  private static final int ESCALA_MONETARIA = 4;

  private final SegmentoRecorrenciaRepository segmentoRepository;
  private final GrupoRecorrenciaRepository grupoRepository;
  private final TransacaoRepository transacaoRepository;
  private final CriarSerieTransacao criarSerie;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;
  private final LocalizarOcorrenciaRecorrente localizarOcorrencia;
  private final ExpansorRecorrencia expansor;
  private final LeitorRruleRecorrencia leitorRrule;
  private final RegrasEdicaoTransacao regras;

  public AlterarEstaEAsProximas(
      SegmentoRecorrenciaRepository segmentoRepository,
      GrupoRecorrenciaRepository grupoRepository,
      TransacaoRepository transacaoRepository,
      CriarSerieTransacao criarSerie,
      ResolverOcorrenciasRecorrentes resolverOcorrencias,
      LocalizarOcorrenciaRecorrente localizarOcorrencia,
      ExpansorRecorrencia expansor,
      LeitorRruleRecorrencia leitorRrule,
      RegrasEdicaoTransacao regras) {
    this.segmentoRepository = segmentoRepository;
    this.grupoRepository = grupoRepository;
    this.transacaoRepository = transacaoRepository;
    this.criarSerie = criarSerie;
    this.resolverOcorrencias = resolverOcorrencias;
    this.localizarOcorrencia = localizarOcorrencia;
    this.expansor = expansor;
    this.leitorRrule = leitorRrule;
    this.regras = regras;
  }

  @Transactional
  public ConsultarTransacoes.Item executar(Comando comando) {
    regras.validarCamposComuns(
        comando.contaId(),
        comando.situacao(),
        comando.tipo(),
        comando.valor(),
        comando.descricao(),
        comando.observacoes(),
        comando.dataFinanceira());
    validarResolucoesInformadas(comando);

    var usuarioId = comando.usuarioId();
    var grupoId =
        segmentoRepository
            .buscarGrupoIdDoUsuario(comando.segmentoId(), usuarioId)
            .orElseThrow(() -> naoEncontrada("Recorrencia"));
    var grupo =
        grupoRepository
            .buscarDoUsuarioParaEdicao(grupoId, usuarioId)
            .orElseThrow(() -> naoEncontrada("Recorrencia"));
    var segmento =
        segmentoRepository
            .buscarDoUsuarioParaEdicao(comando.segmentoId(), usuarioId)
            .orElseThrow(() -> naoEncontrada("Recorrencia"));
    var materializada =
        transacaoRepository.buscarOcorrenciaDoUsuarioParaEdicao(
            segmento.getId(), comando.dataOriginal(), usuarioId);
    var ocorrencia =
        localizarOcorrencia.executar(
            segmento, comando.dataOriginal(), usuarioId, materializada.isPresent());
    var versaoAtual =
        IdentidadeEdicaoTransacao.versaoRecorrencia(
            grupo.getRevisaoSerie(), materializada.map(Transacao::getVersao).orElse(null));
    if (!versaoAtual.equals(comando.versaoEdicao())) {
      throw new RegraNegocioException(
          "VERSAO_DESATUALIZADA", "A ocorrencia foi alterada por outra operacao");
    }
    if (segmentoRepository.existeSegmentoPosteriorAtivo(
        grupo.getId(), segmento.getId(), usuarioId, comando.dataOriginal())) {
      throw new RegraNegocioException(
          "SEGMENTO_NAO_ATUAL", "A edicao deve partir do segmento mais recente do grupo");
    }

    var atual = materializada.orElse(null);
    var contaAtual = atual != null ? atual.getConta() : segmento.getConta();
    var conta = regras.resolverConta(contaAtual, comando.contaId(), usuarioId);
    var categoria =
        regras.resolverCategoria(
            atual != null ? atual.getCategoria() : segmento.getCategoria(),
            comando.categoriaId(),
            usuarioId);
    regras.validarDatas(conta, segmento.getUsuario(), comando.dataFinanceira(), comando.situacao());

    var dados =
        new Dados(
            conta,
            categoria,
            comando.tipo(),
            comando.descricao().strip(),
            comando.observacoes() == null ? null : comando.observacoes().strip(),
            comando.valor().setScale(ESCALA_MONETARIA, RoundingMode.UNNECESSARY),
            comando.dataFinanceira(),
            comando.situacao(),
            RegrasEdicaoTransacao.efetivadoEm(comando.situacao(), atual));
    var contexto = new Contexto(grupo, segmento, atual, ocorrencia, comando, dados);

    if (comando.repeticao() == null) {
      regras.validarMesmaMoeda(contaAtual, conta);
      return converterEmSimples(contexto);
    }
    return iniciarNovoTrecho(contexto);
  }

  private ConsultarTransacoes.Item converterEmSimples(Contexto contexto) {
    var usuarioId = contexto.comando().usuarioId();
    var dataOriginal = contexto.comando().dataOriginal();
    var segmento = contexto.segmento();
    var historico = !listarProtegidasAPartirDe(contexto).isEmpty();

    encerrarProjecao(segmento, dataOriginal);
    var transacao = contexto.atual();
    if (transacao == null) {
      transacao = new Transacao();
      transacao.setUsuario(segmento.getUsuario());
    } else {
      transacao.setGrupoRecorrencia(null);
      transacao.setSegmentoRecorrencia(null);
      transacao.setIdentificadorRecorrencia(null);
      transacao.setExcecaoRecorrencia(false);
    }
    preencherSimples(transacao, contexto.dados());
    transacao.setSegmentoOrigem(segmento);
    transacao.setDataOriginalOrigem(dataOriginal);
    transacao.setNumeroParcelaOrigem(
        RegrasEdicaoTransacao.numeroParcela(segmento, contexto.ocorrencia()));
    if (contexto.atual() == null) {
      transacaoRepository.persist(transacao);
    }

    regras.registrarSupressao(segmento, dataOriginal, usuarioId);
    encerrarGrupoSemProjecao(contexto.grupo(), segmento, dataOriginal, usuarioId, historico);
    contexto.grupo().setRevisaoSerie(contexto.grupo().getRevisaoSerie() + 1);
    transacaoRepository.flush();
    return ConsultarTransacoes.itemTransacaoSimples(transacao);
  }

  private ConsultarTransacoes.Item iniciarNovoTrecho(Contexto contexto) {
    var comando = contexto.comando();
    var usuarioId = comando.usuarioId();
    var dataOriginal = comando.dataOriginal();
    var segmento = contexto.segmento();
    var grupo = contexto.grupo();
    var inicio = contexto.dados().dataFinanceira();
    var repeticao = comando.repeticao();
    if (inicio.isBefore(dataOriginal)) {
      throw new RegraNegocioException(
          "REPETICAO_INVALIDA", "A nova regra deve iniciar na data da ocorrencia ou depois dela");
    }

    var destinoParcelado = repeticao.parcelamento() != null;
    var origemParcelada = grupo.getTipo() == TipoGrupoRecorrencia.PARCELAMENTO;
    if (destinoParcelado && origemParcelada) {
      var numeroAtual = RegrasEdicaoTransacao.numeroParcela(segmento, contexto.ocorrencia());
      if (!Objects.equals(repeticao.parcelamento().numeroPrimeiraParcela(), numeroAtual)) {
        throw new RegraNegocioException(
            "PARCELAMENTO_INVALIDO", "A numeracao da parcela selecionada deve ser preservada");
      }
    }
    var mesmoGrupo =
        destinoParcelado == origemParcelada
            && (!destinoParcelado
                || Objects.equals(
                    repeticao.parcelamento().totalParcelas(),
                    segmento.getQuantidadeTotalOriginal()));

    var regra =
        RepeticaoValidacao.validarEdicao(
            repeticao,
            inicio,
            aceitaMensalAncoradaExistente(segmento, repeticao, destinoParcelado));
    var protegidas = listarProtegidasAPartirDe(contexto);
    var colisoes = colisoes(regra, repeticao.politicaData(), protegidas);
    var decisoes = decidirSobreposicoes(comando, colisoes);

    encerrarProjecao(segmento, dataOriginal);
    if (contexto.atual() != null) {
      transacaoRepository.delete(contexto.atual());
    }
    grupo.setRevisaoSerie(grupo.getRevisaoSerie() + 1);

    var grupoDestino = grupo;
    if (!mesmoGrupo) {
      grupoDestino =
          novoGrupo(
              segmento.getUsuario(),
              contexto.dados().descricao(),
              destinoParcelado
                  ? TipoGrupoRecorrencia.PARCELAMENTO
                  : TipoGrupoRecorrencia.RECORRENCIA);
      encerrarGrupoSemProjecao(grupo, segmento, dataOriginal, usuarioId, !protegidas.isEmpty());
    }

    var dados = contexto.dados();
    var novoSegmento =
        criarSerie.executarNoGrupo(
            grupoDestino,
            new CriarSerieTransacao.Comando(
                segmento.getUsuario(),
                dados.conta(),
                dados.categoria(),
                dados.tipo(),
                dados.descricao(),
                dados.observacoes(),
                dados.valor(),
                inicio,
                repeticao),
            regra);

    var materializadas = new ArrayList<Transacao>();
    if (dados.situacao() == SituacaoTransacao.EFETIVADA) {
      var primeira = new Transacao();
      primeira.setUsuario(segmento.getUsuario());
      primeira.setGrupoRecorrencia(grupoDestino);
      primeira.setSegmentoRecorrencia(novoSegmento);
      primeira.setIdentificadorRecorrencia(inicio);
      primeira.setExcecaoRecorrencia(false);
      preencherSimples(primeira, dados);
      transacaoRepository.persist(primeira);
      materializadas.add(primeira);
    }

    decisoes.forEach(
        (data, decisao) -> {
          // A primeira ocorrência é a escrita explícita e não é suprimida pela resolução.
          if (decisao == ResolucaoSobreposicao.MANTER_SOMENTE_PRESERVADA && !data.equals(inicio)) {
            regras.registrarSupressao(novoSegmento, data, usuarioId);
          }
        });

    transacaoRepository.flush();
    return resolverOcorrencias
        .resolver(List.of(novoSegmento), materializadas, List.of(), inicio, inicio)
        .stream()
        .findFirst()
        .map(ConsultarTransacoes::itemRecorrente)
        .orElseThrow(() -> new IllegalStateException("A primeira ocorrencia nao foi projetada"));
  }

  /**
   * Aceita a forma mensal PADRAO sem dias apenas quando o segmento de origem já a possui: a
   * configuração devolvida pela leitura pode ser reenviada sem exigir mudança de regra.
   */
  private boolean aceitaMensalAncoradaExistente(
      SegmentoRecorrencia segmento, RepeticaoRecorrencia repeticao, boolean destinoParcelado) {
    if (destinoParcelado
        || repeticao.frequencia() != FrequenciaRecorrencia.MONTHLY
        || !repeticao.diasMes().isEmpty()
        || repeticao.politicaData() != PoliticaDataOcorrencia.PADRAO) {
      return false;
    }
    var anterior = leitorRrule.lerSegmento(segmento.getInicio(), segmento.getRrule());
    return anterior.frequencia() == FrequenciaRecorrencia.MONTHLY
        && anterior.diasMes().isEmpty()
        && segmento.getPoliticaData() == PoliticaDataOcorrencia.PADRAO
        && segmento.getGrupo().getTipo() == TipoGrupoRecorrencia.RECORRENCIA;
  }

  /** Encerra a projeção a partir da data original, sem estender um segmento já encerrado. */
  private static void encerrarProjecao(SegmentoRecorrencia segmento, LocalDate dataOriginal) {
    if (dataOriginal.isEqual(segmento.getInicio())) {
      segmento.setStatus(StatusRecorrencia.CANCELADO);
      return;
    }
    if (segmento.getFim() == null || !segmento.getFim().isBefore(dataOriginal)) {
      segmento.setFim(dataOriginal.minusDays(1));
    }
    segmento.setStatus(StatusRecorrencia.CONCLUIDO);
  }

  private void encerrarGrupoSemProjecao(
      GrupoRecorrencia grupo,
      SegmentoRecorrencia segmento,
      LocalDate dataOriginal,
      UUID usuarioId,
      boolean historicoProtegido) {
    if (segmentoRepository.existeSegmentoAtivoDoGrupo(grupo.getId(), usuarioId)) {
      return;
    }
    var possuiHistorico =
        historicoProtegido
            || dataOriginal.isAfter(segmento.getInicio())
            || segmentoRepository.existeSegmentoAnteriorDoGrupo(
                grupo.getId(), usuarioId, segmento.getInicio());
    grupo.setStatus(possuiHistorico ? StatusRecorrencia.CONCLUIDO : StatusRecorrencia.CANCELADO);
  }

  private GrupoRecorrencia novoGrupo(Usuario usuario, String descricao, TipoGrupoRecorrencia tipo) {
    var grupo = new GrupoRecorrencia();
    grupo.setUsuario(usuario);
    grupo.setDescricao(descricao);
    grupo.setTipo(tipo);
    grupo.setStatus(StatusRecorrencia.ATIVO);
    grupoRepository.persist(grupo);
    return grupo;
  }

  /** Protegidas do grupo com data original igual ou posterior ao corte, excluída a selecionada. */
  private List<Transacao> listarProtegidasAPartirDe(Contexto contexto) {
    var atual = contexto.atual();
    return transacaoRepository
        .listarProtegidasDoGrupoAPartirDe(
            contexto.grupo().getId(),
            contexto.comando().usuarioId(),
            contexto.comando().dataOriginal())
        .stream()
        .filter(protegida -> atual == null || !Objects.equals(protegida.getId(), atual.getId()))
        .toList();
  }

  /**
   * Datas financeiras da nova regra que coincidem com protegidas. A verificação usa a regra
   * inteira, sem expandir além das protegidas, que são finitas.
   */
  private Map<LocalDate, List<Transacao>> colisoes(
      RegraRecorrencia regra, PoliticaDataOcorrencia politica, List<Transacao> protegidas) {
    var colisoes = new TreeMap<LocalDate, List<Transacao>>();
    for (var protegida : protegidas) {
      var data = protegida.getDataFinanceira();
      if (!data.isBefore(regra.inicio()) && ocorre(regra, politica, data)) {
        colisoes.computeIfAbsent(data, ignorada -> new ArrayList<>()).add(protegida);
      }
    }
    return colisoes;
  }

  private boolean ocorre(RegraRecorrencia regra, PoliticaDataOcorrencia politica, LocalDate data) {
    return expansor.expandir(regra, data, data, politica).stream()
        .anyMatch(ocorrencia -> ocorrencia.dataOriginal().equals(data));
  }

  /** Escolha específica prevalece sobre a política geral; a data deve ser colisão da versão. */
  private static Map<LocalDate, ResolucaoSobreposicao> decidirSobreposicoes(
      Comando comando, Map<LocalDate, List<Transacao>> colisoes) {
    var especificas = new HashMap<LocalDate, ResolucaoSobreposicao>();
    for (var escolha : comando.resolucoes()) {
      var resolucao = interpretar(escolha.resolucao());
      var data = escolha.dataOriginalCandidata();
      if (!colisoes.containsKey(data) || especificas.putIfAbsent(data, resolucao) != null) {
        throw new RegraNegocioException(
            "RESOLUCAO_SOBREPOSICAO_INVALIDA",
            "Decisao duplicada ou para data que nao e candidata sobreposta");
      }
    }
    var decisoes = new TreeMap<LocalDate, ResolucaoSobreposicao>();
    for (var data : colisoes.keySet()) {
      decisoes.put(data, especificas.getOrDefault(data, comando.politicaSobreposicao()));
    }
    return decisoes;
  }

  private static ResolucaoSobreposicao interpretar(String valor) {
    try {
      return ResolucaoSobreposicao.valueOf(valor);
    } catch (IllegalArgumentException exception) {
      throw new RegraNegocioException(
          "RESOLUCAO_SOBREPOSICAO_INVALIDA", "Resolucao de sobreposicao desconhecida");
    }
  }

  private static void validarResolucoesInformadas(Comando comando) {
    if (comando.repeticao() == null) {
      if (comando.resolucaoInformada()) {
        throw new RegraNegocioException(
            "RESOLUCAO_SOBREPOSICAO_INVALIDA",
            "Resolucao nao se aplica a alteracao que resulta em transacao simples");
      }
      return;
    }
    if (comando.politicaSobreposicao() == null) {
      throw new ValidacaoException(
          "politicaSobreposicao", "Politica de sobreposicao obrigatoria ao criar novo trecho");
    }
    if (comando.resolucoes() == null) {
      throw new ValidacaoException(
          "resolucoesSobreposicao",
          "Escolhas de sobreposicao obrigatorias ao criar novo trecho; use [] para nenhuma");
    }
  }

  private static void preencherSimples(Transacao transacao, Dados dados) {
    transacao.setConta(dados.conta());
    transacao.setCategoria(dados.categoria());
    transacao.setTipo(dados.tipo());
    transacao.setSituacao(dados.situacao());
    transacao.setDescricao(dados.descricao());
    transacao.setObservacoes(dados.observacoes());
    transacao.setValor(dados.valor());
    transacao.setDataFinanceira(dados.dataFinanceira());
    transacao.setEfetivadoEm(dados.efetivadoEm());
  }

  private static RecursoNaoEncontradoException naoEncontrada(String recurso) {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrada");
  }

  private record Contexto(
      GrupoRecorrencia grupo,
      SegmentoRecorrencia segmento,
      Transacao atual,
      OcorrenciaRecorrencia ocorrencia,
      Comando comando,
      Dados dados) {}

  private record Dados(
      ContaFinanceira conta,
      Categoria categoria,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      SituacaoTransacao situacao,
      Instant efetivadoEm) {}

  /**
   * Comando da alteração de série com escopo ESTA_E_AS_PROXIMAS. {@code repeticao} é nula quando o
   * destino é transação simples; {@code politicaSobreposicao} e {@code resolucoes} são obrigatórios
   * somente quando há novo trecho.
   */
  public record Comando(
      UUID usuarioId,
      UUID segmentoId,
      LocalDate dataOriginal,
      String versaoEdicao,
      UUID contaId,
      UUID categoriaId,
      SituacaoTransacao situacao,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      RepeticaoRecorrencia repeticao,
      ResolucaoSobreposicao politicaSobreposicao,
      List<Escolha> resolucoes,
      boolean resolucaoInformada) {

    /** Decisão textual recebida; a validação do valor ocorre no caso de uso. */
    public record Escolha(LocalDate dataOriginalCandidata, String resolucao) {}
  }
}
