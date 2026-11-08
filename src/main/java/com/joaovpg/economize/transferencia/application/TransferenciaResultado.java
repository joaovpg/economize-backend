package com.joaovpg.economize.transferencia.application;

import com.joaovpg.economize.transferencia.SituacaoTransferencia;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransferenciaResultado(
    UUID id,
    String versaoEdicao,
    UUID contaOrigemId,
    UUID contaDestinoId,
    SituacaoTransferencia situacao,
    String descricao,
    String observacoes,
    BigDecimal valor,
    LocalDate dataFinanceira,
    Instant efetivadoEm) {}
