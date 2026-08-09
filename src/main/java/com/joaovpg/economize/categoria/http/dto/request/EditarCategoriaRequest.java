package com.joaovpg.economize.categoria.http.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record EditarCategoriaRequest(
    @NotBlank String nome,
    @Pattern(regexp = "^\\s*(#[0-9A-Fa-f]{6})?\\s*$") String cor,
    UUID categoriaPaiId,
    @NotNull Boolean ativo) {}
