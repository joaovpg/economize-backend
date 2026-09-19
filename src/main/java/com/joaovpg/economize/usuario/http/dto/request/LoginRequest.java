package com.joaovpg.economize.usuario.http.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record LoginRequest(
    @Schema(description = "E-mail cadastrado.", example = "maria@example.com", required = true)
        @NotBlank @Email String email,
    @Schema(
            description = "Senha cadastrada. Nunca é devolvida pela API.",
            example = "senha-segura",
            format = "password",
            writeOnly = true,
            required = true)
        @NotBlank String senha) {}
