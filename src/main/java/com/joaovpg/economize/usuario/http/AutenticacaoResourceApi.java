package com.joaovpg.economize.usuario.http;

import com.joaovpg.economize.usuario.http.dto.request.CadastroRequest;
import com.joaovpg.economize.usuario.http.dto.request.LoginRequest;
import com.joaovpg.economize.usuario.http.dto.response.CsrfTokenResponse;
import com.joaovpg.economize.usuario.http.dto.response.UsuarioResponse;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/autenticacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AutenticacaoResourceApi {
  @POST
  @Path("/cadastro")
  @PermitAll
  @Tag(name = "Autenticação")
  @Operation(
      operationId = "cadastrarUsuario",
      summary = "Cadastra um usuário",
      description =
          "Cria um usuário e inicia a sessão. A resposta define os cookies "
              + "`economize_token` (HttpOnly) e `economize_csrf`; o frontend deve preservar "
              + "esses cookies para as próximas requisições.")
  @APIResponse(
      responseCode = "201",
      description = "Usuário cadastrado e sessão iniciada.",
      headers =
          @Header(
              name = "Set-Cookie",
              description =
                  "Define os cookies economize_token (HttpOnly) e economize_csrf para a sessão.",
              schema = @Schema(type = SchemaType.STRING)),
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = UsuarioResponse.class)))
  @APIResponse(responseCode = "422", description = "E-mail já cadastrado ou outra regra de negócio violada.")
  RestResponse<UsuarioResponse> cadastrar(
      @Valid
          @RequestBody(
              description = "Dados do novo usuário.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = CadastroRequest.class),
                      examples =
                          @ExampleObject(
                              name = "cadastroBasico",
                              value =
                                  "{\"nome\":\"Maria Silva\",\"email\":\"maria@example.com\","
                                      + "\"senha\":\"senha-segura\",\"timezone\":\"America/Sao_Paulo\"}")))
          CadastroRequest request);

  @POST
  @Path("/login")
  @PermitAll
  @Tag(name = "Autenticação")
  @Operation(
      operationId = "autenticarUsuario",
      summary = "Autentica um usuário",
      description =
          "Valida as credenciais e inicia a sessão. O token de acesso é entregue somente no "
              + "cookie HttpOnly `economize_token`; o corpo retorna o valor de `csrfToken` que "
              + "deve ser enviado no header `X-CSRF-Token` em requisições mutáveis.")
  @APIResponse(
      responseCode = "200",
      description = "Login realizado com sucesso.",
      headers =
          @Header(
              name = "Set-Cookie",
              description =
                  "Define os cookies economize_token (HttpOnly) e economize_csrf para a sessão.",
              schema = @Schema(type = SchemaType.STRING)),
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = CsrfTokenResponse.class)))
  @APIResponse(responseCode = "401", description = "E-mail ou senha inválidos.")
  RestResponse<CsrfTokenResponse> login(
      @Valid
          @RequestBody(
              description = "Credenciais do usuário.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = LoginRequest.class),
                      examples =
                          @ExampleObject(
                              name = "login",
                              value = "{\"email\":\"maria@example.com\",\"senha\":\"senha-segura\"}")))
          LoginRequest request);

  @POST
  @Path("/logout")
  @PermitAll
  @Tag(name = "Autenticação")
  @Operation(
      operationId = "encerrarSessao",
      summary = "Encerra a sessão",
      description = "Expira os cookies de autenticação e de proteção CSRF da sessão atual.")
  @APIResponse(
      responseCode = "204",
      description = "Sessão encerrada com sucesso.",
      headers =
          @Header(
              name = "Set-Cookie",
              description = "Expira os cookies economize_token e economize_csrf da sessão.",
              schema = @Schema(type = SchemaType.STRING)))
  RestResponse<Void> logout();
}
