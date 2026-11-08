package com.joaovpg.economize.recorrencia.application;

import com.joaovpg.economize.recorrencia.ExpansorRecorrencia;
import com.joaovpg.economize.recorrencia.LeitorRruleRecorrencia;
import com.joaovpg.economize.recorrencia.OcorrenciaRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SupressaoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.UUID;

/** Localiza a ocorrência de um segmento pela data original, para leitura ou alteração. */
@ApplicationScoped
public class LocalizarOcorrenciaRecorrente {
  private final ExpansorRecorrencia expansor;
  private final LeitorRruleRecorrencia leitorRrule;
  private final SupressaoRecorrenciaRepository supressaoRepository;

  public LocalizarOcorrenciaRecorrente(
      ExpansorRecorrencia expansor,
      LeitorRruleRecorrencia leitorRrule,
      SupressaoRecorrenciaRepository supressaoRepository) {
    this.expansor = expansor;
    this.leitorRrule = leitorRrule;
    this.supressaoRepository = supressaoRepository;
  }

  /**
   * Uma ocorrência materializada é sempre localizável; uma virtual precisa estar projetada e sem
   * supressão.
   */
  public OcorrenciaRecorrencia executar(
      SegmentoRecorrencia segmento, LocalDate dataOriginal, UUID usuarioId, boolean materializada) {
    var regra = leitorRrule.lerSegmento(segmento.getInicio(), segmento.getRrule());
    var ocorrencia =
        expansor
            .expandir(regra, segmento.getInicio(), dataOriginal, segmento.getPoliticaData())
            .stream()
            .filter(item -> item.dataOriginal().equals(dataOriginal))
            .findFirst();
    if (materializada) {
      return ocorrencia.orElseThrow(() -> RecorrenciaValidacao.naoEncontrado("Ocorrencia"));
    }
    if (ocorrencia.isEmpty()
        || (segmento.getFim() != null && segmento.getFim().isBefore(dataOriginal))
        || segmento.getStatus() == StatusRecorrencia.CANCELADO
        || segmento.getGrupo().getStatus() == StatusRecorrencia.CANCELADO
        || supressaoRepository.existe(segmento.getId(), dataOriginal, usuarioId)) {
      throw RecorrenciaValidacao.naoEncontrado("Ocorrencia");
    }
    return ocorrencia.get();
  }
}
