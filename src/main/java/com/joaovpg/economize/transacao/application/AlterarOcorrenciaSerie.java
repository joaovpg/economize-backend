package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.OcorrenciaRecorrencia;
import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.application.LocalizarOcorrenciaRecorrente;
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
  private final CriarSerieTransacao criarSerie;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;
  private final LocalizarOcorrenciaRecorrente localizarOcorrencia;
  private final RegrasEdicaoTransacao regras;

  public AlterarOcorrenciaSerie(
      SegmentoRecorrenciaRepository segmentoRepository,
      GrupoRecorrenciaRepository grupoRepository,
      TransacaoRepository transacaoRepository,
      CriarSerieTransacao criarSerie,
      ResolverOcorrenciasRecorrentes resolverOcorrencias,
      LocalizarOcorrenciaRecorrente localizarOcorrencia,
      RegrasEdicaoTransacao regras) {
    this.segmentoRepository = segmentoRepository;
    this.grupoRepository = grupoRepository;
    this.transacaoRepository = transacaoRepository;
    this.criarSerie = criarSerie;
    this.resolverOcorrencias = resolverOcorrencias;
    this.localizarOcorrencia = localizarOcorrencia;
    this.regras = regras;
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

    var ocorrencia =
        localizarOcorrencia.executar(
            segmento, comando.dataOriginal(), comando.usuarioId(), materializada.isPresent());
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
    var conta = regras.resolverConta(contaAtual, comando.contaId(), comando.usuarioId());
    var categoria =
        regras.resolverCategoria(
            atual != null ? atual.getCategoria() : segmento.getCategoria(),
            comando.categoriaId(),
            comando.usuarioId());
    regras.validarDatas(conta, usuario, comando.dataFinanceira(), comando.situacao());

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

    if (comando.repeticao() == null) {
      regras.validarMesmaMoeda(contaAtual, conta);
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
    transacao.setNumeroParcelaOrigem(RegrasEdicaoTransacao.numeroParcela(segmento, ocorrencia));
    if (atual == null) {
      transacaoRepository.persist(transacao);
    }

    regras.registrarSupressao(segmento, comando.dataOriginal(), comando.usuarioId());
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
    regras.registrarSupressao(segmento, comando.dataOriginal(), comando.usuarioId());
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

  private void validar(Comando comando) {
    if (!ESCOPO_APLICAVEL.equals(comando.escopo())) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO", "Escopo nao aplicavel a esta ocorrencia de serie");
    }
    if (comando.resolucaoInformada()) {
      throw new RegraNegocioException(
          "RESOLUCAO_SOBREPOSICAO_INVALIDA", "Resolucao nao se aplica a esta alteracao");
    }
    regras.validarCamposComuns(
        comando.contaId(),
        comando.situacao(),
        comando.tipo(),
        comando.valor(),
        comando.descricao(),
        comando.observacoes(),
        comando.dataFinanceira());
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
