package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.recorrencia.ExpansorRecorrencia;
import com.joaovpg.economize.recorrencia.RegraRecorrencia;
import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.application.LocalizarOcorrenciaRecorrente;
import com.joaovpg.economize.recorrencia.application.RepeticaoValidacao;
import com.joaovpg.economize.recorrencia.application.ResolverOcorrenciasRecorrentes;
import com.joaovpg.economize.recorrencia.enums.PoliticaDataOcorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.shared.exception.ValidacaoException;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.ResolucaoSobreposicao;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calcula a prévia de uma alteração de transação simples ou de ocorrência de série, sem gravar
 * nada, sem alterar saldos e sem gerar token. Compara as novas ocorrências com as protegidas
 * posteriores ao corte pela data financeira, e informa a contagem exata das colisões fora do
 * período sem expandir repetições infinitas.
 */
@ApplicationScoped
public class PreverAlteracaoTransacao {
  private static final String ESCOPO_APLICAVEL = "ESTA_E_AS_PROXIMAS";
  private static final int MAXIMO_MESES = 12;
  private static final int ESCALA_MONETARIA = 4;
  private static final ResolucaoSobreposicao RESOLUCAO_SUGERIDA =
      ResolucaoSobreposicao.MANTER_SOMENTE_PRESERVADA;
  private static final List<ResolucaoSobreposicao> RESOLUCOES_PERMITIDAS =
      List.of(ResolucaoSobreposicao.MANTER_SOMENTE_PRESERVADA, ResolucaoSobreposicao.MANTER_AMBAS);

  private final TransacaoRepository transacaoRepository;
  private final SegmentoRecorrenciaRepository segmentoRepository;
  private final LocalizarOcorrenciaRecorrente localizarOcorrencia;
  private final ExpansorRecorrencia expansor;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;
  private final RegrasEdicaoTransacao regras;

  public PreverAlteracaoTransacao(
      TransacaoRepository transacaoRepository,
      SegmentoRecorrenciaRepository segmentoRepository,
      LocalizarOcorrenciaRecorrente localizarOcorrencia,
      ExpansorRecorrencia expansor,
      ResolverOcorrenciasRecorrentes resolverOcorrencias,
      RegrasEdicaoTransacao regras) {
    this.transacaoRepository = transacaoRepository;
    this.segmentoRepository = segmentoRepository;
    this.localizarOcorrencia = localizarOcorrencia;
    this.expansor = expansor;
    this.resolverOcorrencias = resolverOcorrencias;
    this.regras = regras;
  }

  @Transactional
  public Resultado executarSimples(UUID transacaoId, Comando comando) {
    validarPrevia(comando);
    if (comando.escopoInformado()) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO", "Escopo nao se aplica a transacao simples");
    }
    validarCamposComuns(comando);

    var transacao =
        transacaoRepository
            .buscarDoUsuario(transacaoId, comando.usuarioId())
            .orElseThrow(() -> naoEncontrada("Transacao"));
    validarTransacaoSimples(transacao);
    var versaoAtual = IdentidadeEdicaoTransacao.versao(transacao.getVersao());
    verificarVersao(versaoAtual, comando, "A transacao foi alterada por outra operacao");

    var conta = regras.resolverConta(transacao.getConta(), comando.contaId(), comando.usuarioId());
    regras.validarMesmaMoeda(transacao.getConta(), conta);
    regras.resolverCategoria(transacao.getCategoria(), comando.categoriaId(), comando.usuarioId());
    regras.validarDatas(
        conta, transacao.getUsuario(), comando.dataFinanceira(), comando.situacao());

    var projecao = projecaoDoComando(comando, comando.dataFinanceira());
    return calcular(versaoAtual, comando, projecao, List.of());
  }

  @Transactional
  public Resultado executarOcorrencia(
      IdentidadeEdicaoTransacao.ChaveOcorrencia chave, Comando comando) {
    validarPrevia(comando);
    if (!ESCOPO_APLICAVEL.equals(comando.escopo())) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO", "Escopo nao aplicavel a esta ocorrencia de serie");
    }
    validarCamposComuns(comando);

    var segmento =
        segmentoRepository
            .buscarDoUsuario(chave.segmentoId(), comando.usuarioId())
            .orElseThrow(() -> naoEncontrada("Recorrencia"));
    var grupo = segmento.getGrupo();
    var materializada =
        transacaoRepository.buscarOcorrenciaDoUsuario(
            segmento.getId(), chave.dataOriginal(), comando.usuarioId());
    localizarOcorrencia.executar(
        segmento, chave.dataOriginal(), comando.usuarioId(), materializada.isPresent());
    var versaoAtual =
        IdentidadeEdicaoTransacao.versaoRecorrencia(
            grupo.getRevisaoSerie(), materializada.map(Transacao::getVersao).orElse(null));
    verificarVersao(versaoAtual, comando, "A ocorrencia foi alterada por outra operacao");

    var atual = materializada.orElse(null);
    var contaAtual = atual != null ? atual.getConta() : segmento.getConta();
    var conta = regras.resolverConta(contaAtual, comando.contaId(), comando.usuarioId());
    regras.resolverCategoria(
        atual != null ? atual.getCategoria() : segmento.getCategoria(),
        comando.categoriaId(),
        comando.usuarioId());
    regras.validarDatas(conta, segmento.getUsuario(), comando.dataFinanceira(), comando.situacao());
    if (comando.repeticao() == null) {
      regras.validarMesmaMoeda(contaAtual, conta);
    }

    var projecao = projecaoDoComando(comando, comando.dataFinanceira());
    var protegidas =
        transacaoRepository
            .listarProtegidasDoGrupoAPartirDe(
                grupo.getId(), comando.usuarioId(), chave.dataOriginal())
            .stream()
            .filter(protegida -> atual == null || !Objects.equals(protegida.getId(), atual.getId()))
            .toList();
    return calcular(versaoAtual, comando, projecao, protegidas);
  }

  private Resultado calcular(
      String versaoAtual, Comando comando, Projecao projecao, List<Transacao> protegidas) {
    var inicioPeriodo = comando.inicio().atDay(1);
    var fimPeriodo = comando.fim().atEndOfMonth();

    var candidatas = candidatasEntre(projecao, inicioPeriodo, fimPeriodo);
    var protegidasNoPeriodo =
        protegidas.stream()
            .filter(protegida -> dentro(protegida.getDataFinanceira(), inicioPeriodo, fimPeriodo))
            .toList();
    var preservadas =
        resolverOcorrencias.resolver(
            List.of(), protegidasNoPeriodo, List.of(), inicioPeriodo, fimPeriodo);
    var preservadasPorData =
        preservadas.stream()
            .collect(
                Collectors.groupingBy(
                    ResolverOcorrenciasRecorrentes.Resultado::dataFinanceira,
                    HashMap::new,
                    Collectors.toList()));

    var sobreposicoes =
        candidatas.stream()
            .filter(candidata -> preservadasPorData.containsKey(candidata.dataFinanceira()))
            .map(
                candidata ->
                    new Sobreposicao(
                        candidata.dataOriginal(),
                        candidata.dataFinanceira(),
                        preservadasPorData.get(candidata.dataFinanceira()).stream()
                            .map(ResolverOcorrenciasRecorrentes.Resultado::chaveEdicao)
                            .sorted()
                            .toList(),
                        RESOLUCAO_SUGERIDA,
                        RESOLUCOES_PERMITIDAS))
            .toList();

    var quantidadeForaDoPeriodo =
        protegidas.stream()
            .map(Transacao::getDataFinanceira)
            .filter(data -> !dentro(data, inicioPeriodo, fimPeriodo))
            .distinct()
            .filter(data -> contem(projecao, data))
            .count();

    return new Resultado(
        versaoAtual,
        comando.inicio(),
        comando.fim(),
        comando.politicaSobreposicao(),
        preservadas.stream().map(ConsultarTransacoes::itemRecorrente).toList(),
        candidatas,
        sobreposicoes,
        quantidadeForaDoPeriodo);
  }

  private Projecao projecaoDoComando(Comando comando, LocalDate inicio) {
    var impacto =
        (comando.tipo() == TipoTransacao.RECEITA ? comando.valor() : comando.valor().negate())
            .setScale(ESCALA_MONETARIA, RoundingMode.UNNECESSARY);
    if (comando.repeticao() == null) {
      return new Projecao(inicio, null, null, null, impacto);
    }
    var repeticao = comando.repeticao();
    var regra = RepeticaoValidacao.validar(repeticao, inicio);
    var parcelamento = repeticao.parcelamento();
    return new Projecao(
        inicio,
        regra,
        repeticao.politicaData(),
        parcelamento == null ? null : parcelamento.numeroPrimeiraParcela(),
        impacto);
  }

  private List<Candidata> candidatasEntre(Projecao projecao, LocalDate inicio, LocalDate fim) {
    if (projecao.regra() == null) {
      if (!dentro(projecao.inicio(), inicio, fim)) {
        return List.of();
      }
      return List.of(new Candidata(projecao.inicio(), projecao.inicio(), projecao.impacto(), null));
    }
    return expansor.expandir(projecao.regra(), inicio, fim, projecao.politica()).stream()
        .map(
            ocorrencia ->
                new Candidata(
                    ocorrencia.dataOriginal(),
                    ocorrencia.dataOriginal(),
                    projecao.impacto(),
                    projecao.numeroPrimeiraParcela() == null
                        ? null
                        : projecao.numeroPrimeiraParcela() + ocorrencia.numeroOcorrencia() - 1))
        .toList();
  }

  /** Indica se a data financeira pertence à projeção, sem expandir além dela. */
  private boolean contem(Projecao projecao, LocalDate data) {
    if (projecao.regra() == null) {
      return projecao.inicio().equals(data);
    }
    if (data.isBefore(projecao.regra().inicio())) {
      return false;
    }
    return expansor.expandir(projecao.regra(), data, data, projecao.politica()).stream()
        .anyMatch(ocorrencia -> ocorrencia.dataOriginal().equals(data));
  }

  private static boolean dentro(LocalDate data, LocalDate inicio, LocalDate fim) {
    return !data.isBefore(inicio) && !data.isAfter(fim);
  }

  private void validarPrevia(Comando comando) {
    if (comando.inicio() == null || comando.fim() == null) {
      throw new ValidacaoException("periodo", "Periodo obrigatorio");
    }
    var meses = comando.inicio().until(comando.fim(), ChronoUnit.MONTHS) + 1;
    if (meses < 1 || meses > MAXIMO_MESES) {
      throw new ValidacaoException("periodo", "O periodo deve ter de 1 a 12 meses");
    }
    if (comando.politicaSobreposicao() != ResolucaoSobreposicao.MANTER_SOMENTE_PRESERVADA) {
      throw new ValidacaoException(
          "politicaSobreposicao", "A previa aceita somente MANTER_SOMENTE_PRESERVADA");
    }
  }

  private void validarCamposComuns(Comando comando) {
    regras.validarCamposComuns(
        comando.contaId(),
        comando.situacao(),
        comando.tipo(),
        comando.valor(),
        comando.descricao(),
        comando.observacoes(),
        comando.dataFinanceira());
  }

  private void validarTransacaoSimples(Transacao transacao) {
    if (transacaoRepository.estaVinculadaATransferencia(transacao.getId())
        || transacao.getGrupoRecorrencia() != null
        || transacao.getSegmentoRecorrencia() != null) {
      throw naoEncontrada("Transacao");
    }
  }

  private static void verificarVersao(String versaoAtual, Comando comando, String mensagem) {
    if (!versaoAtual.equals(comando.versaoEdicao())) {
      throw new RegraNegocioException("VERSAO_DESATUALIZADA", mensagem);
    }
  }

  private static RecursoNaoEncontradoException naoEncontrada(String recurso) {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", recurso + " nao encontrada");
  }

  /** Nova projeção: primeira ocorrência em {@code inicio}, com regra quando há repetição. */
  private record Projecao(
      LocalDate inicio,
      RegraRecorrencia regra,
      PoliticaDataOcorrencia politica,
      Integer numeroPrimeiraParcela,
      BigDecimal impacto) {}

  /** Ocorrência que a nova regra produziria, com impacto assinado. */
  public record Candidata(
      LocalDate dataOriginal, LocalDate dataFinanceira, BigDecimal valor, Integer numeroParcela) {}

  /** Candidata que colide com ao menos uma ocorrência protegida posterior ao corte. */
  public record Sobreposicao(
      LocalDate dataOriginalCandidata,
      LocalDate dataFinanceira,
      List<String> chavesPreservadas,
      ResolucaoSobreposicao resolucaoSugerida,
      List<ResolucaoSobreposicao> resolucoesPermitidas) {}

  public record Resultado(
      String versaoEdicao,
      YearMonth inicio,
      YearMonth fim,
      ResolucaoSobreposicao politicaSobreposicao,
      List<ConsultarTransacoes.Item> itensPreservados,
      List<Candidata> ocorrenciasNovas,
      List<Sobreposicao> sobreposicoes,
      long quantidadeSobreposicoesForaPeriodo) {}

  /**
   * Comando da prévia. {@code escopo} é o texto recebido ou nulo; {@code escopoInformado} indica se
   * o campo veio no corpo. {@code repeticao} é nula quando o destino é transação simples.
   */
  public record Comando(
      UUID usuarioId,
      String versaoEdicao,
      String escopo,
      boolean escopoInformado,
      UUID contaId,
      UUID categoriaId,
      SituacaoTransacao situacao,
      TipoTransacao tipo,
      String descricao,
      String observacoes,
      BigDecimal valor,
      LocalDate dataFinanceira,
      RepeticaoRecorrencia repeticao,
      YearMonth inicio,
      YearMonth fim,
      ResolucaoSobreposicao politicaSobreposicao) {}
}
