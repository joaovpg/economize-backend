package com.joaovpg.economize.recorrencia.http.dto.request;

import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record EfetivarOcorrenciaRecorrenteRequest(
    @Schema(
            description =
                "Data financeira usada na transação efetivada; quando omitida, usa dataOriginal.",
            examples = {"2026-04-06"},
            format = "date",
            nullable = true)
        LocalDate dataFinanceira) {}
