package com.joaovpg.economize.categoria.http.dto.response;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CategoriaResponse(
    @Schema(
            description = "Identificador da categoria.",
            example = "00000000-0000-0000-0000-000000000003",
            required = true)
        UUID id,
    @Schema(description = "Nome da categoria.", example = "Alimentação", required = true)
        String nome,
    @Schema(
            description = "Cor hexadecimal para exibição.",
            example = "#E67E22",
            nullable = true,
            required = false)
        String cor,
    @Schema(
            description = "Identificador da categoria pai, quando houver.",
            nullable = true,
            required = false)
        UUID categoriaPaiId,
    @Schema(description = "Indica se a categoria está ativa.", example = "true", required = true)
        boolean ativo) {}
