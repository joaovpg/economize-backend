package com.joaovpg.economize.usuario.http.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CsrfTokenResponse(
    @Schema(
            description =
                "Valor que deve ser repetido no header X-CSRF-Token em requisições POST, PUT e"
                    + " DELETE.",
            example = "6d8f2e4a...",
            readOnly = true,
            required = true)
        String csrfToken) {}
