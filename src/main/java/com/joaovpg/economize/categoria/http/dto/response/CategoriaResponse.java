package com.joaovpg.economize.categoria.http.dto.response;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CategoriaResponse(
    @Schema(
            description = "Identificador da categoria.",
            examples = {"00000000-0000-0000-0000-000000000003"},
            required = true)
        UUID id,
    @Schema(
            description = "Nome da categoria.",
            examples = {"Alimentação"},
            required = true)
        String nome,
    @Schema(
            description = "Cor hexadecimal para exibição.",
            examples = {"#E67E22"},
            nullable = true)
        String cor,
    @Schema(description = "Identificador da categoria pai, quando houver.", nullable = true)
        UUID categoriaPaiId,
    @Schema(
            description = "Indica se a categoria está ativa.",
            examples = {"true"},
            required = true)
        boolean ativo) {}
