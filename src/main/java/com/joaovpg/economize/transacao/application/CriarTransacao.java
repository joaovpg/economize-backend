package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RepeticaoRecorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
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

@ApplicationScoped
public class CriarTransacao {
  private static final int ESCALA_MONETARIA = 4;

  private final UsuarioRepository usuarioRepository;
  private final ContaFinanceiraRepository contaRepository;
  private final CategoriaRepository categoriaRepository;
  private final TransacaoRepository transacaoRepository;
  private final CriarSerieTransacao criarSerie;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;

  public CriarTransacao(
      UsuarioRepository usuarioRepository,
      ContaFinanceiraRepository contaRepository,
      CategoriaRepository categoriaRepository,
      TransacaoRepository transacaoRepository,
      CriarSerieTransacao criarSerie,
      ResolverOcorrenciasRecorrentes resolverOcorrencias) {
    this.usuarioRepository = usuarioRepository;
    this.contaRepository = contaRepository;
    this.categoriaRepository = categoriaRepository;
    this.transacaoRepository = transacaoRepository;
    this.criarSerie = criarSerie;
    this.resolverOcorrencias = resolverOcorrencias;
  }

  @Transactional
  public ConsultarTransacoes.Item executar(Comando comando) {
    validar(comando);
    var usuario =
        usuarioRepository
            .buscarAtivo(comando.usuarioId())
            .orElseThrow(() -> recursoNaoEncontrado("Usuario"));
    if (comando.situacao() == SituacaoTransacao.EFETIVADA
        && comando.dataFinanceira().isAfter(LocalDate.now(ZoneId.of(usuario.getTimezone())))) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_FUTURA", "Uma transacao efetivada nao pode ter data financeira futura");
    }
    var conta =
        contaRepository
            .buscarAtivaDoUsuario(comando.contaId(), comando.usuarioId())
            .orElseThrow(() -> recursoNaoEncontrado("Conta"));
    if (comando.dataFinanceira().isBefore(conta.getDataSaldoInicial())) {
      throw new RegraNegocioException(
          "DATA_FINANCEIRA_ANTERIOR_SALDO_INICIAL",
          "A data financeira nao pode ser anterior a data do saldo inicial da conta");
    }
    Categoria categoria =
        comando.categoriaId() == null
            ? null
            : categoriaRepository
                .buscarAtivaDoUsuario(comando.categoriaId(), comando.usuarioId())
                .orElseThrow(() -> recursoNaoEncontrado("Categoria"));
    var descricao = comando.descricao().strip();
    var observacoes = comando.observacoes() == null ? null : comando.observacoes().strip();
    var valor = comando.valor().setScale(ESCALA_MONETARIA, RoundingMode.UNNECESSARY);

    if (comando.repeticao() != null) {
      return criarSerie(comando, usuario, conta, categoria, descricao, observacoes, valor);
    }

    var transacao = new Transacao();
    transacao.setUsuario(usuario);
    transacao.setConta(conta);
    transacao.setCategoria(categoria);
    transacao.setTipo(comando.tipo());
    transacao.setSituacao(comando.situacao());
    transacao.setDescricao(descricao);
    transacao.setObservacoes(observacoes);
    transacao.setValor(valor);
    transacao.setDataFinanceira(comando.dataFinanceira());
    transacao.setEfetivadoEm(
        comando.situacao() == SituacaoTransacao.EFETIVADA ? Instant.now() : null);
    transacaoRepository.persist(transacao);
    return ConsultarTransacoes.itemTransacaoSimples(transacao);
  }

  /**
   * Cria a série e devolve a primeira ocorrência no mesmo formato da leitura. Quando efetivada, ela
   * é materializada uma única vez; quando planejada, continua virtual e é projetada pela regra.
   */
  private ConsultarTransacoes.Item criarSerie(
      Comando comando,
      Usuario usuario,
      ContaFinanceira conta,
      Categoria categoria,
      String descricao,
      String observacoes,
      BigDecimal valor) {
    var inicio = comando.dataFinanceira();
    var segmento =
        criarSerie.executar(
            new CriarSerieTransacao.Comando(
                usuario,
                conta,
                categoria,
                comando.tipo(),
                descricao,
                observacoes,
                valor,
                inicio,
                comando.repeticao()));

    var materializadas = new ArrayList<Transacao>();
    if (comando.situacao() == SituacaoTransacao.EFETIVADA) {
      var primeira = new Transacao();
      primeira.setUsuario(usuario);
      primeira.setConta(conta);
      primeira.setCategoria(categoria);
      primeira.setGrupoRecorrencia(segmento.getGrupo());
      primeira.setSegmentoRecorrencia(segmento);
      primeira.setTipo(comando.tipo());
      primeira.setSituacao(SituacaoTransacao.EFETIVADA);
      primeira.setDescricao(descricao);
      primeira.setObservacoes(observacoes);
      primeira.setValor(valor);
      primeira.setDataFinanceira(inicio);
      primeira.setIdentificadorRecorrencia(inicio);
      primeira.setExcecaoRecorrencia(false);
      primeira.setEfetivadoEm(Instant.now().truncatedTo(ChronoUnit.MICROS));
      transacaoRepository.persist(primeira);
      materializadas.add(primeira);
    }

    return primeiraOcorrencia(segmento, materializadas, inicio);
  }

  private ConsultarTransacoes.Item primeiraOcorrencia(
      SegmentoRecorrencia segmento, List<Transacao> materializadas, LocalDate inicio) {
    return resolverOcorrencias
        .resolver(List.of(segmento), materializadas, List.of(), inicio, inicio)
        .stream()
        .findFirst()
        .map(ConsultarTransacoes::itemRecorrente)
        .orElseThrow(() -> new IllegalStateException("A primeira ocorrencia nao foi projetada"));
  }

  private void validar(Comando comando) {
    if (comando.situacao() == null) {
      throw new RegraNegocioException(
          "SITUACAO_TRANSACAO_INVALIDA", "Situacao da transacao invalida");
    }
    if (comando.tipo() == null) {
      throw new RegraNegocioException("TIPO_TRANSACAO_INVALIDO", "Tipo da transacao invalido");
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

  private RecursoNaoEncontradoException recursoNaoEncontrado(String recurso) {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrado");
  }

  public record Comando(
      UUID usuarioId,
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
