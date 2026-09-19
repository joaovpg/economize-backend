package com.joaovpg.economize.usuario.http.dto.request;

import com.joaovpg.economize.usuario.http.validation.TimezoneValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CadastroRequest(
    @Schema(description = "Nome de exibição do usuário.", example = "Maria Silva", maxLength = 120)
    @NotBlank @Size(max = 120) String nome,
    @Schema(description = "E-mail usado para autenticação.", example = "maria@example.com", maxLength = 320)
    @NotBlank @Email @Size(max = 320) String email,
    @Schema(
        description = "Senha do usuário. Nunca é devolvida nas respostas.",
        example = "senha-segura",
        minLength = 8,
        maxLength = 128,
        format = "password",
        writeOnly = true)
    @NotNull @Size(min = 8, max = 128) String senha,
    @Schema(
        description = "Timezone IANA usada para validar datas efetivadas.",
        example = "America/Sao_Paulo",
        maxLength = 80)
    @NotBlank @Size(max = 80) @TimezoneValido String timezone) {
  public CadastroRequest {
    if (nome != null) {
      nome = nome.strip();
    }
    if (email != null) {
      email = email.strip().toLowerCase(Locale.ROOT);
    }
  }
}
