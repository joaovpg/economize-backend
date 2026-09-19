package com.joaovpg.economize.usuario.http.dto.response;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UsuarioResponse(
    @Schema(
            description = "Identificador do usuário.",
            example = "00000000-0000-0000-0000-000000000001")
        UUID id,
    @Schema(description = "Nome de exibição.", example = "Maria Silva") String nome,
    @Schema(description = "E-mail cadastrado.", example = "maria@example.com") String email,
    @Schema(description = "Timezone IANA do usuário.", example = "America/Sao_Paulo")
        String timezone) {}
