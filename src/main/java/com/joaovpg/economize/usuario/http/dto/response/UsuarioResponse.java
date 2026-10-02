package com.joaovpg.economize.usuario.http.dto.response;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UsuarioResponse(
    @Schema(
            description = "Identificador do usuário.",
            examples = {"00000000-0000-0000-0000-000000000001"},
            required = true)
        UUID id,
    @Schema(
            description = "Nome de exibição.",
            examples = {"Maria Silva"},
            required = true)
        String nome,
    @Schema(
            description = "E-mail cadastrado.",
            examples = {"maria@example.com"},
            required = true)
        String email,
    @Schema(
            description = "Timezone IANA do usuário.",
            examples = {"America/Sao_Paulo"},
            required = true)
        String timezone) {}
