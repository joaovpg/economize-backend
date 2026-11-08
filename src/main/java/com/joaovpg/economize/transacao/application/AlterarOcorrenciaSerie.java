package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.ExpansorRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.LeitorRruleRecorrencia;
import com.joaovpg.economize.recorrencia.OcorrenciaRecorrencia;
import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SupressaoRecorrencia;
import com.joaovpg.economize.recorrencia.SupressaoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.application.CriarSerieTransacao;
import com.joaovpg.economize.recorrencia.application.ResolverOcorrenciasRecorrentes;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Altera uma única ocorrência de uma série (escopo SOMENTE_ESTA). A ocorrência pode virar uma
 * transação simples, que mantém a série como histórico, ou iniciar uma nova série; em ambos os
 * casos a ocorrência original é suprimida e a série anterior continua.
 */
@ApplicationScoped
public class AlterarOcorrenciaSerie {
  private static final String ESCOPO_APLICAVEL = "SOMENTE_ESTA";
  private static final int ESCALA_MONETARIA = 4;

  private final SegmentoRecorrenciaRepository segmentoRepository;
  private final GrupoRecorrenciaRepository grupoRepository;
  private final TransacaoRepository transacaoRepository;
  private final SupressaoRecorrenciaRepository supressaoRepository;
  private final ContaFinanceiraRepository contaRepository;
  private final CategoriaRepository categoriaRepository;
  private final CriarSerieTransacao criarSerie;
  private final ExpansorRecorrencia expansor;
  private final LeitorRruleRecorrencia leitorRrule;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;

  public AlterarOcorrenciaSerie(
      SegmentoRecorrenciaRepository segmentoRepository,
      GrupoRecorrenciaRepository grupoRepository,
      TransacaoRepository transacaoRepository,
      SupressaoRecorrenciaRepository supressaoRepository,
      ContaFinanceiraRepository contaRepository,
      CategoriaRepository categoriaRepository,
      CriarSerieTransacao criarSerie,
      ExpansorRecorrencia expansor,
      LeitorRruleRecorrencia leitorRrule,
      ResolverOcorrenciasRecorrentes resolverOcorrencias) {
    this.segmentoRepository = segmentoRepository;
    this.grupoRepository = grupoRepository;
    this.transacaoRepository = transacaoRepository;
    this.supressaoRepository = supressaoRepository;
    this.contaRepository = contaRepository;
    this.categoriaRepository = categoriaRepository;
    this.criarSerie = criarSerie;
    this.expansor = expansor;
    this.leitorRrule = leitorRrule;
    this.resolverOcorrencias = resolverOcorrencias;
  }

  @Transactional
  public ConsultarTransacoes.Item executar(Comando comando) {
    validar(comando);

    var grupoId =
        segmentoRepository
            .buscarGrupoIdDoUsuario(comando.segmentoId(), comando.usuarioId())
            .orElseThrow(() -> naoEncontrada("Recorrencia"));
    var grupo =
        grupoRepository
            .buscarDoUsuarioParaEdicao(grupoId, comando.usuarioId())
            .orElseThrow(() -> naoEncontrada("Recorrencia"));
    var segmento =
        segmentoRepository
            .buscarDoUsuarioParaEdicao(comando.segmentoId(), comando.usuarioId())
            .orElseThrow(() -> naoEncontrada("Recorrencia"));
    var materializada =
        transacaoRepository.buscarOcorrenciaDoUsuarioParaEdicao(
            segmento.getId(), comando.dataOriginal(), comando.usuarioId());

    var ocorrencia = localizarOcorrencia(segmento, comando, materializada.isPresent());
    var versaoAtual =
        IdentidadeEdicaoTransacao.versaoRecorrencia(
            grupo.getRevisaoSerie(), materializada.map(Transacao::getVersao).orElse(null));
    if (!versaoAtual.equals(comando.versaoEdicao())) {
      throw new RegraNegocioException(
          "VERSAO_DESATUALIZADA", "A ocorrencia foi alterada por outra operacao");
    }

    var atual = materializada.orElse(null);
    var usuario = segmento.getUsuario();
    var contaAtual = atual != null ? atual.getConta() : segmento.getConta();
    var conta = resolverConta(contaAtual, comando);
    var categoria =
        resolverCategoria(atual != null ? atual.getCategoria() : segmento.getCategoria(), comando);

    if (comando.dataFinanceira().isBefore(conta.getDataSaldoInicial())) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL",
          "A data financeira nao pode ser anterior a data do saldo inicial da conta");
    }
    if (comando.situacao() == SituacaoTransacao.EFETIVADA
        && comando.dataFinanceira().isAfter(LocalDate.now(ZoneId.of(usuario.getTimezone())))) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_FUTURA", "Uma transacao efetivada nao pode ter data financeira futura");
    }

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
            efetivadoEm(comando.situacao(), atual));

    if (comando.repeticao() == null) {
      if (!conta.getMoeda().equals(contaAtual.getMoeda())) {
        throw new RegraNegocioException(
            "MOEDA_CONTA_INCOMPATIVEL", "A nova conta deve usar a mesma moeda da conta atual");
      }
      return individualizarComoSimples(segmento, grupo, atual, comando, dados, ocorrencia);
    }
    return iniciarNovaSerie(segmento, grupo, atual, comando, dados);
  }

  private ConsultarTransacoes.Item individualizarComoSimples(
      SegmentoRecorrencia segmento,
      GrupoRecorrencia grupo,
      Transacao atual,
      Comando comando,
      Dados dados,
      OcorrenciaRecorrencia ocorrencia) {
    var transacao = atual;
    if (transacao == null) {
      transacao = new Transacao();
      transacao.setUsuario(segmento.getUsuario());
    } else {
      transacao.setGrupoRecorrencia(null);
      transacao.setSegmentoRecorrencia(null);
      transacao.setIdentificadorRecorrencia(null);
      transacao.setExcecaoRecorrencia(false);
    }
    preencherSimples(transacao, dados);
    transacao.setSegmentoOrigem(segmento);
    transacao.setDataOriginalOrigem(comando.dataOriginal());
    transacao.setNumeroParcelaOrigem(numeroParcela(segmento, ocorrencia));
    if (atual == null) {
      transacaoRepository.persist(transacao);
    }

    criarSupressao(segmento, comando.dataOriginal(), comando.usuarioId());
    grupo.setRevisaoSerie(grupo.getRevisaoSerie() + 1);
    transacaoRepository.flush();
    return ConsultarTransacoes.itemTransacaoSimples(transacao);
  }

  private ConsultarTransacoes.Item iniciarNovaSerie(
      SegmentoRecorrencia segmento,
      GrupoRecorrencia grupo,
      Transacao atual,
      Comando comando,
      Dados dados) {
    if (atual != null) {
      transacaoRepository.delete(atual);
    }
    criarSupressao(segmento, comando.dataOriginal(), comando.usuarioId());
    grupo.setRevisaoSerie(grupo.getRevisaoSerie() + 1);

    var novoSegmento =
        criarSerie.executar(
            new CriarSerieTransacao.Comando(
                segmento.getUsuario(),
                dados.conta(),
                dados.categoria(),
                dados.tipo(),
                dados.descricao(),
                dados.observacoes(),
                dados.valor(),
                dados.dataFinanceira(),
                comando.repeticao()));

    var materializadas = new ArrayList<Transacao>();
    if (dados.situacao() == SituacaoTransacao.EFETIVADA) {
      var primeira = new Transacao();
      primeira.setUsuario(segmento.getUsuario());
      primeira.setGrupoRecorrencia(novoSegmento.getGrupo());
      primeira.setSegmentoRecorrencia(novoSegmento);
      primeira.setIdentificadorRecorrencia(dados.dataFinanceira());
      primeira.setExcecaoRecorrencia(false);
      preencherSimples(primeira, dados);
      transacaoRepository.persist(primeira);
      materializadas.add(primeira);
    }

    return resolverOcorrencias
        .resolver(
            List.of(novoSegmento),
            materializadas,
            List.of(),
            dados.dataFinanceira(),
            dados.dataFinanceira())
        .stream()
        .findFirst()
        .map(ConsultarTransacoes::itemRecorrente)
        .orElseThrow(() -> new IllegalStateException("A primeira ocorrencia nao foi projetada"));
  }

  private void preencherSimples(Transacao transacao, Dados dados) {
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

  /**
   * Localiza a ocorrência pela data original. Uma ocorrência materializada é sempre localizável;
   * uma virtual precisa estar projetada e sem supressão.
   */
  private OcorrenciaRecorrencia localizarOcorrencia(
      SegmentoRecorrencia segmento, Comando comando, boolean materializada) {
    var regra = leitorRrule.lerSegmento(segmento.getInicio(), segmento.getRrule());
    var ocorrencia =
        expansor
            .expandir(
                regra, segmento.getInicio(), comando.dataOriginal(), segmento.getPoliticaData())
            .stream()
            .filter(item -> item.dataOriginal().equals(comando.dataOriginal()))
            .findFirst();
    if (materializada) {
      return ocorrencia.orElseThrow(() -> naoEncontrada("Ocorrencia"));
    }
    if (ocorrencia.isEmpty()
        || (segmento.getFim() != null && segmento.getFim().isBefore(comando.dataOriginal()))
        || segmento.getStatus() == StatusRecorrencia.CANCELADO
        || segmento.getGrupo().getStatus() == StatusRecorrencia.CANCELADO
        || supressaoRepository.existe(
            segmento.getId(), comando.dataOriginal(), comando.usuarioId())) {
      throw naoEncontrada("Ocorrencia");
    }
    return ocorrencia.get();
  }

  private void criarSupressao(
      SegmentoRecorrencia segmento, LocalDate dataOriginal, UUID usuarioId) {
    if (supressaoRepository.existe(segmento.getId(), dataOriginal, usuarioId)) {
      return;
    }
    var supressao = new SupressaoRecorrencia();
    supressao.setUsuario(segmento.getUsuario());
    supressao.setGrupo(segmento.getGrupo());
    supressao.setSegmento(segmento);
    supressao.setIdentificadorRecorrencia(dataOriginal);
    supressaoRepository.persist(supressao);
  }

  private static Integer numeroParcela(
      SegmentoRecorrencia segmento, OcorrenciaRecorrencia ocorrencia) {
    return segmento.getNumeroPrimeiraParcela() == null
        ? null
        : segmento.getNumeroPrimeiraParcela() + ocorrencia.numeroOcorrencia() - 1;
  }

  private static Instant efetivadoEm(SituacaoTransacao situacao, Transacao atual) {
    if (situacao == SituacaoTransacao.PLANEJADA) {
      return null;
    }
    if (atual != null
        && atual.getSituacao() == SituacaoTransacao.EFETIVADA
        && atual.getEfetivadoEm() != null) {
      return atual.getEfetivadoEm();
    }
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private ContaFinanceira resolverConta(ContaFinanceira contaAtual, Comando comando) {
    if (comando.contaId().equals(contaAtual.getId())) {
      return contaAtual;
    }
    return contaRepository
        .buscarAtivaDoUsuario(comando.contaId(), comando.usuarioId())
        .orElseThrow(() -> naoEncontrada("Conta"));
  }

  private Categoria resolverCategoria(Categoria categoriaAtual, Comando comando) {
    if (comando.categoriaId() == null) {
      return null;
    }
    if (categoriaAtual != null && comando.categoriaId().equals(categoriaAtual.getId())) {
      return categoriaAtual;
    }
    return categoriaRepository
        .buscarAtivaDoUsuario(comando.categoriaId(), comando.usuarioId())
        .orElseThrow(() -> naoEncontrada("Categoria"));
  }

  private void validar(Comando comando) {
    if (!ESCOPO_APLICAVEL.equals(comando.escopo())) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO", "Escopo nao aplicavel a esta ocorrencia de serie");
    }
    if (comando.resolucaoInformada()) {
      throw new RegraNegocioException(
          "RESOLUCAO_SOBREPOSICAO_INVALIDA", "Resolucao nao se aplica a esta alteracao");
    }
    if (comando.contaId() == null) {
      throw new RegraNegocioException("CONTA_OBRIGATORIA", "Conta obrigatoria");
    }
    if (comando.situacao() == null) {
      throw new RegraNegocioException(
          "SITUACAO_TRANSACAO_INVALIDA", "Situacao da transacao invalida");
    }
    if (comando.tipo() != TipoTransacao.RECEITA && comando.tipo() != TipoTransacao.DESPESA) {
      throw new RegraNegocioException(
          "TIPO_TRANSACAO_INVALIDO", "Recorrencias aceitam somente receita ou despesa");
    }
    if (comando.valor() == null
        || comando.valor().signum() <= 0
        || comando.valor().scale() > 4
        || comando.valor().setScale(4, RoundingMode.UNNECESSARY).precision() > 19) {
      throw new RegraNegocioException("VALOR_TRANSACAO_INVALIDO", "Valor da transacao invalido");
    }
    if (comando.descricao() == null
        || comando.descricao().isBlank()
        || comando.descricao().strip().length() > 255) {
      throw new RegraNegocioException(
          "DESCRICAO_TRANSACAO_INVALIDA", "Descricao da transacao invalida");
    }
    if (comando.observacoes() != null && comando.observacoes().length() > 2000) {
      throw new RegraNegocioException(
          "OBSERVACOES_TRANSACAO_INVALIDAS", "Observacoes da transacao invalidas");
    }
    if (comando.dataFinanceira() == null) {
      throw new RegraNegocioException("DATA_FINANCEIRA_OBRIGATORIA", "Data financeira obrigatoria");
    }
  }

  private static RecursoNaoEncontradoException naoEncontrada(String recurso) {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrada");
  }

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
   * Comando da alteração de uma ocorrência. {@code escopo} é o valor textual recebido ou nulo;
   * {@code repeticao} é nula quando o destino é transação simples.
   */
  public record Comando(
      UUID usuarioId,
      UUID segmentoId,
      LocalDate dataOriginal,
      String versaoEdicao,
      String escopo,
      boolean resolucaoInformada,
      UUID contaId,
      UUID categoriaId,
      SituacaoTransacao situacao,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      RepeticaoRecorrencia repeticao) {}
}
