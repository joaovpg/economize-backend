package com.joaovpg.economize.categoria.http.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CadastrarCategoriaRequest(
    @Schema(description = "Nome da categoria.", example = "Alimentação", maxLength = 80) @NotBlank String nome,
    @Schema(
            description = "Cor hexadecimal opcional para exibição no frontend.",
            example = "#E67E22",
            pattern = "^\\s*(#[0-9A-Fa-f]{6})?\\s*$",
            nullable = true)
        @Pattern(regexp = "^\\s*(#[0-9A-Fa-f]{6})?\\s*$")
        String cor,
    @Schema(
            description = "Categoria pai opcional; deve pertencer ao mesmo usuário.",
            example = "00000000-0000-0000-0000-000000000003",
            nullable = true)
        UUID categoriaPaiId) {}
