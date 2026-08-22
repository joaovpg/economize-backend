package com.joaovpg.economize.usuario.http;

import com.joaovpg.economize.shared.http.LogHttpErrors;
import com.joaovpg.economize.usuario.application.AutenticarUsuario;
import com.joaovpg.economize.usuario.application.CadastrarUsuario;
import com.joaovpg.economize.usuario.http.dto.request.CadastroRequest;
import com.joaovpg.economize.usuario.http.dto.request.LoginRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
  public Response cadastrar(@Valid CadastroRequest request) {
    var comando = mapper.toCommand(request);
    var resultado = cadastrarUsuario.executar(comando);
    var resposta = mapper.toResponse(resultado.usuario());
    var cookiesSessao = cookies.criar(resultado.token());
    return Response.status(Response.Status.CREATED)
        .entity(resposta)
        .cookie(cookiesSessao.token(), cookiesSessao.csrf())
        .build();
  }

  @POST
  @Path("/login")
  @PermitAll
  public Response login(@Valid LoginRequest request) {
    var comando = mapper.toCommand(request);
    var resultado = autenticarUsuario.executar(comando);
    var cookiesSessao = cookies.criar(resultado.token());
    return Response.ok().cookie(cookiesSessao.token(), cookiesSessao.csrf()).build();
  }

  @POST
  @Path("/logout")
  @PermitAll
  public Response logout() {
    var cookiesExpirados = cookies.expirar();
    return Response.noContent().cookie(cookiesExpirados.token(), cookiesExpirados.csrf()).build();
  }
}
