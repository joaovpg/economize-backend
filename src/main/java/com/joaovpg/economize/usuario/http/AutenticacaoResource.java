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

  AutenticacaoResource(
      AutenticarUsuario autenticarUsuario,
      CadastrarUsuario cadastrarUsuario,
      AutenticacaoHttpMapper mapper) {
    this.autenticarUsuario = autenticarUsuario;
    this.cadastrarUsuario = cadastrarUsuario;
    this.mapper = mapper;
  }

  @POST
  @Path("/cadastro")
  @PermitAll
  public Response cadastrar(@Valid CadastroRequest request) {
    var comando = mapper.toCommand(request);
    var resultado = cadastrarUsuario.executar(comando);
    return Response.status(Response.Status.CREATED).entity(mapper.toResponse(resultado)).build();
  }

  @POST
  @Path("/login")
  @PermitAll
  public Response login(@Valid LoginRequest request) {
    var comando = mapper.toCommand(request);
    var resultado = autenticarUsuario.executar(comando);
    var response = mapper.toResponse(resultado);
    return Response.ok(response).build();
  }
}
