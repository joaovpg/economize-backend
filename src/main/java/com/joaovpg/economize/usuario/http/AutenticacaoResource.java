package com.joaovpg.economize.usuario.http;

import com.joaovpg.economize.shared.http.LogHttpErrors;
import com.joaovpg.economize.usuario.application.AutenticarUsuario;
import com.joaovpg.economize.usuario.application.CadastrarUsuario;
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
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/autenticacao")
@LogHttpErrors
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AutenticacaoResource {
  private final AutenticarUsuario autenticarUsuario;
  private final CadastrarUsuario cadastrarUsuario;
  private final AutenticacaoHttpMapper mapper;
  private final CookiesAutenticacao cookies;

  AutenticacaoResource(
      AutenticarUsuario autenticarUsuario,
      CadastrarUsuario cadastrarUsuario,
      AutenticacaoHttpMapper mapper,
      CookiesAutenticacao cookies) {
    this.autenticarUsuario = autenticarUsuario;
    this.cadastrarUsuario = cadastrarUsuario;
    this.mapper = mapper;
    this.cookies = cookies;
  }

  @POST
  @Path("/cadastro")
  @PermitAll
  @APIResponse(
      responseCode = "201",
      description = "Usuário cadastrado com sucesso.",
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
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public RestResponse<UsuarioResponse> cadastrar(@Valid CadastroRequest request) {
    var comando = mapper.toCommand(request);
    var resultado = cadastrarUsuario.executar(comando);
    var resposta = mapper.toResponse(resultado.usuario());
    var cookiesSessao = cookies.criar(resultado.token());
    return RestResponse.ResponseBuilder.<UsuarioResponse>create(RestResponse.Status.CREATED)
        .entity(resposta)
        .cookie(cookiesSessao.token(), cookiesSessao.csrf())
        .build();
  }

  @POST
  @Path("/login")
  @PermitAll
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
  @APIResponse(responseCode = "401", description = "Falha de autenticação.")
  public RestResponse<CsrfTokenResponse> login(@Valid LoginRequest request) {
    var comando = mapper.toCommand(request);
    var resultado = autenticarUsuario.executar(comando);
    var cookiesSessao = cookies.criar(resultado.token());
    var resposta = new CsrfTokenResponse(cookiesSessao.csrf().getValue());

    return RestResponse.ResponseBuilder.ok(resposta)
        .cookie(cookiesSessao.token(), cookiesSessao.csrf())
        .build();
  }

  @POST
  @Path("/logout")
  @PermitAll
  @APIResponse(
      responseCode = "204",
      description = "Sessão encerrada com sucesso.",
      headers =
          @Header(
              name = "Set-Cookie",
              description = "Expira os cookies economize_token e economize_csrf da sessão.",
              schema = @Schema(type = SchemaType.STRING)))
  public RestResponse<Void> logout() {
    var cookiesExpirados = cookies.expirar();
    return RestResponse.ResponseBuilder.<Void>noContent()
        .cookie(cookiesExpirados.token(), cookiesExpirados.csrf())
        .build();
  }
}
