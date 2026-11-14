package com.joaovpg.economize.recorrencia.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Metadados de uma ocorrência de série.
 *
 * @param grupoId grupo ao qual o segmento pertence
 * @param segmentoId segmento que gera a ocorrência
 * @param inicioSegmento data de início real do segmento
 * @param dataOriginal data que identifica a ocorrência dentro do segmento
 * @param numeroParcela número da parcela; nulo fora de parcelamentos
 * @param totalParcelas total contratado original; nulo fora de parcelamentos
 */
public record SerieRecorrencia(
    UUID grupoId,
    UUID segmentoId,
    LocalDate inicioSegmento,
    LocalDate dataOriginal,
    Integer numeroParcela,
    Integer totalParcelas) {}
