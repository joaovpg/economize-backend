package com.joaovpg.economize.categoria.http.dto.response;

import java.util.UUID;

public record CategoriaResponse(
    UUID id, String nome, String cor, UUID categoriaPaiId, boolean ativo) {}
