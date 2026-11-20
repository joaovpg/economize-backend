package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.application.LocalizarOcorrenciaRecorrente;
import com.joaovpg.economize.recorrencia.domain.LeitorRruleRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RepeticaoRecorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.transacao.domain.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.domain.SituacaoTransacao;
import com.joaovpg.economize.transacao.domain.TipoTransacao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Altera uma única ocorrência de uma série (escopo SOMENTE_ESTA). A ocorrência vira um registro
 * físico vinculado ao segmento, identificado pela data original, ou é alterada no próprio registro
 * quando já era física. A série segue projetando as demais datas e nenhuma supressão é gravada. A
 * repetição precisa ser a lida: mudar a regra ou converter em transação simples é de
 * ESTA_E_AS_PROXIMAS.
 */
@ApplicationScoped
public class AlterarOcorrenciaSerie {
  private static final String ESCOPO_APLICAVEL = "SOMENTE_ESTA";
  private static final int ESCALA_MONETARIA = 4;

  private final SegmentoRecorrenciaRepository segmentoRepository;
  private final GrupoRecorrenciaRepository grupoRepository;
  private final TransacaoRepository transacaoRepository;
  private final ResolverOcorrenciasRecorrentes resolverOcorrencias;
  private final LocalizarOcorrenciaRecorrente localizarOcorrencia;
  private final LeitorRruleRecorrencia leitorRrule;
  private final RegrasEdicaoTransacao regras;

  public AlterarOcorrenciaSerie(
      SegmentoRecorrenciaRepository segmentoRepository,
      GrupoRecorrenciaRepository grupoRepository,
      TransacaoRepository transacaoRepository,
      ResolverOcorrenciasRecorrentes resolverOcorrencias,
      LocalizarOcorrenciaRecorrente localizarOcorrencia,
      LeitorRruleRecorrencia leitorRrule,
      RegrasEdicaoTransacao regras) {
    this.segmentoRepository = segmentoRepository;
    this.grupoRepository = grupoRepository;
    this.transacaoRepository = transacaoRepository;
    this.resolverOcorrencias = resolverOcorrencias;
    this.localizarOcorrencia = localizarOcorrencia;
    this.leitorRrule = leitorRrule;
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

    localizarOcorrencia.executar(
        segmento, comando.dataOriginal(), comando.usuarioId(), materializada.isPresent());
    var versaoAtual =
        IdentidadeEdicaoTransacao.versaoRecorrencia(
            grupo.getRevisaoSerie(), materializada.map(Transacao::getVersao).orElse(null));
    if (!versaoAtual.equals(comando.versaoEdicao())) {
      throw new RegraNegocioException(
          "VERSAO_DESATUALIZADA", "A ocorrencia foi alterada por outra operacao");
    }
    exigirRepeticaoLida(segmento, comando.repeticao());

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
    regras.validarMesmaMoeda(contaAtual, conta);

    var transacao =
        atual != null ? atual : novaOcorrenciaVinculada(segmento, grupo, comando.dataOriginal());
    preencher(
        transacao,
        new Dados(
            conta,
            categoria,
            comando.tipo(),
            comando.descricao().strip(),
            comando.observacoes() == null ? null : comando.observacoes().strip(),
            comando.valor().setScale(ESCALA_MONETARIA, RoundingMode.UNNECESSARY),
            comando.dataFinanceira(),
            comando.situacao(),
            RegrasEdicaoTransacao.efetivadoEm(comando.situacao(), atual)));
    transacao.setExcecaoRecorrencia(true);
    if (atual == null) {
      transacaoRepository.persist(transacao);
    }
    grupo.setRevisaoSerie(grupo.getRevisaoSerie() + 1);
    transacaoRepository.flush();

    return resolverOcorrencias
        .resolver(List.of(), List.of(transacao), List.of(), null, null)
        .stream()
        .findFirst()
        .map(ConsultarTransacoes::itemRecorrente)
        .orElseThrow(() -> new IllegalStateException("A ocorrencia vinculada nao foi lida"));
  }

  /**
   * A repetição recebida deve ser a lida, sem o número do dia útil, que é calculado pelo servidor.
   * Qualquer outra, inclusive nula, responde ESCOPO_INVALIDO antes de qualquer gravação.
   */
  private void exigirRepeticaoLida(SegmentoRecorrencia segmento, RepeticaoRecorrencia recebida) {
    var regra = leitorRrule.ler(segmento.getInicio(), segmento.getRrule());
    var lida = RepeticaoRecorrencia.de(segmento, regra).semNumeroDiaUtil();
    if (recebida == null || !lida.equals(recebida.semNumeroDiaUtil())) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO",
          "Com SOMENTE_ESTA a repeticao deve ser a lida; para alterar a regra use"
              + " ESTA_E_AS_PROXIMAS");
    }
  }

  private static Transacao novaOcorrenciaVinculada(
      SegmentoRecorrencia segmento, GrupoRecorrencia grupo, LocalDate dataOriginal) {
    var transacao = new Transacao();
    transacao.setUsuario(segmento.getUsuario());
    transacao.setGrupoRecorrencia(grupo);
    transacao.setSegmentoRecorrencia(segmento);
    transacao.setIdentificadorRecorrencia(dataOriginal);
    return transacao;
  }

  private static void preencher(Transacao transacao, Dados dados) {
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
   * {@code repeticao} deve ser a configuração lida, sem o número do dia útil, e é nula quando
   * ausente.
   */
  public record Comando(
      UUID usuarioId,
      UUID segmentoId,
      LocalDate dataOriginal,
      String versaoEdicao,
      String escopo,
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
