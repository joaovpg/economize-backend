package com.joaovpg.economize.transacao.application;

import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SupressaoRecorrencia;
import com.joaovpg.economize.recorrencia.SupressaoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.application.LocalizarOcorrenciaRecorrente;
import com.joaovpg.economize.recorrencia.domain.StatusRecorrencia;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.transacao.EscopoEdicaoTransacao;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Exclui uma ocorrência de série. Com SOMENTE_ESTA, a ocorrência é removida e suprimida de forma
 * durável; com ESTA_E_AS_PROXIMAS, a projeção é encerrada a partir dela, as planejadas do trecho
 * são removidas e as protegidas posteriores permanecem. Toda exclusão revisa o grupo.
 */
@ApplicationScoped
public class ExcluirOcorrenciaSerie {
  private final SegmentoRecorrenciaRepository segmentoRepository;
  private final GrupoRecorrenciaRepository grupoRepository;
  private final TransacaoRepository transacaoRepository;
  private final SupressaoRecorrenciaRepository supressaoRepository;
  private final LocalizarOcorrenciaRecorrente localizarOcorrencia;

  public ExcluirOcorrenciaSerie(
      SegmentoRecorrenciaRepository segmentoRepository,
      GrupoRecorrenciaRepository grupoRepository,
      TransacaoRepository transacaoRepository,
      SupressaoRecorrenciaRepository supressaoRepository,
      LocalizarOcorrenciaRecorrente localizarOcorrencia) {
    this.segmentoRepository = segmentoRepository;
    this.grupoRepository = grupoRepository;
    this.transacaoRepository = transacaoRepository;
    this.supressaoRepository = supressaoRepository;
    this.localizarOcorrencia = localizarOcorrencia;
  }

  @Transactional
  public void executar(
      UUID usuarioId, UUID segmentoId, LocalDate dataOriginal, String escopo, String versaoEdicao) {
    var alcance = escopo(escopo);

    var grupoId =
        segmentoRepository
            .buscarGrupoIdDoUsuario(segmentoId, usuarioId)
            .orElseThrow(() -> naoEncontrada());
    var grupo =
        grupoRepository
            .buscarDoUsuarioParaEdicao(grupoId, usuarioId)
            .orElseThrow(() -> naoEncontrada());
    var segmento =
        segmentoRepository
            .buscarDoUsuarioParaEdicao(segmentoId, usuarioId)
            .orElseThrow(() -> naoEncontrada());
    var materializada =
        transacaoRepository.buscarOcorrenciaDoUsuarioParaEdicao(
            segmentoId, dataOriginal, usuarioId);
    localizarOcorrencia.executar(segmento, dataOriginal, usuarioId, materializada.isPresent());

    var versaoAtual =
        IdentidadeEdicaoTransacao.versaoRecorrencia(
            grupo.getRevisaoSerie(), materializada.map(Transacao::getVersao).orElse(null));
    if (!versaoAtual.equals(versaoEdicao)) {
      throw new RegraNegocioException(
          "VERSAO_DESATUALIZADA", "A ocorrencia foi alterada por outra operacao");
    }

    materializada.ifPresent(transacaoRepository::delete);
    if (alcance == EscopoEdicaoTransacao.SOMENTE_ESTA) {
      suprimir(segmento, dataOriginal, usuarioId);
    } else {
      encerrarAPartirDe(segmento, dataOriginal, usuarioId);
    }
    grupo.setRevisaoSerie(grupo.getRevisaoSerie() + 1);
    transacaoRepository.flush();
  }

  private static EscopoEdicaoTransacao escopo(String texto) {
    if (texto == null) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO", "Escopo obrigatorio para ocorrencia de serie");
    }
    try {
      return EscopoEdicaoTransacao.valueOf(texto);
    } catch (IllegalArgumentException exception) {
      throw new RegraNegocioException(
          "ESCOPO_INVALIDO", "Escopo nao suportado para ocorrencia de serie");
    }
  }

  private void suprimir(SegmentoRecorrencia segmento, LocalDate dataOriginal, UUID usuarioId) {
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

  private void encerrarAPartirDe(
      SegmentoRecorrencia segmento, LocalDate dataOriginal, UUID usuarioId) {
    removerPlanejadasAPartirDe(segmento, dataOriginal, usuarioId);
    if (dataOriginal.isEqual(segmento.getInicio())) {
      segmento.setStatus(StatusRecorrencia.CANCELADO);
    } else {
      segmento.setFim(dataOriginal.minusDays(1));
      segmento.setStatus(StatusRecorrencia.CONCLUIDO);
    }

    var grupoId = segmento.getGrupo().getId();
    var segmentosFuturos =
        segmentoRepository.listarAtivosDoGrupoAPartirDe(
            grupoId, segmento.getId(), usuarioId, dataOriginal);
    for (var futuro : segmentosFuturos) {
      removerPlanejadasAPartirDe(futuro, futuro.getInicio(), usuarioId);
      futuro.setStatus(StatusRecorrencia.CANCELADO);
    }

    if (!segmentoRepository.existeSegmentoAtivoDoGrupo(grupoId, usuarioId)) {
      var possuiHistoricoVirtual =
          dataOriginal.isAfter(segmento.getInicio())
              || segmentoRepository.existeSegmentoAnteriorDoGrupo(
                  grupoId, usuarioId, segmento.getInicio());
      segmento
          .getGrupo()
          .setStatus(
              possuiHistoricoVirtual ? StatusRecorrencia.CONCLUIDO : StatusRecorrencia.CANCELADO);
    }
  }

  private void removerPlanejadasAPartirDe(
      SegmentoRecorrencia segmento, LocalDate inicio, UUID usuarioId) {
    transacaoRepository
        .listarOcorrenciasDoSegmentoAPartirDe(segmento.getId(), inicio, usuarioId)
        .stream()
        .filter(transacao -> transacao.getSituacao() == SituacaoTransacao.PLANEJADA)
        .forEach(transacaoRepository::delete);
  }

  private static RecursoNaoEncontradoException naoEncontrada() {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Ocorrencia nao encontrada");
  }
}
