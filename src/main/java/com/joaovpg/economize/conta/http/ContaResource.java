package com.joaovpg.economize.conta.http;

import com.joaovpg.economize.conta.application.CadastrarConta;
import com.joaovpg.economize.conta.application.EditarConta;
import com.joaovpg.economize.conta.application.ListarContas;
import com.joaovpg.economize.conta.http.dto.request.CadastrarContaRequest;
import com.joaovpg.economize.conta.http.dto.request.EditarContaRequest;
import com.joaovpg.economize.shared.http.LogHttpErrors;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/contas")
@LogHttpErrors
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("usuario")
public class ContaResource {
  private final CadastrarConta cadastrarConta;
  private final EditarConta editarConta;
  private final ListarContas listarContas;
  private final ContaHttpMapper mapper;
  private final JsonWebToken token;

  ContaResource(
      CadastrarConta cadastrarConta,
      EditarConta editarConta,
      ListarContas listarContas,
      ContaHttpMapper mapper,
      JsonWebToken token) {
    this.cadastrarConta = cadastrarConta;
    this.editarConta = editarConta;
    this.listarContas = listarContas;
    this.mapper = mapper;
    this.token = token;
  }

  @POST
  public Response cadastrar(@Valid CadastrarContaRequest request) {
    var resultado = cadastrarConta.executar(mapper.toCommand(usuarioId(), request));
    return Response.status(Response.Status.CREATED).entity(mapper.toResponse(resultado)).build();
  }

  @GET
  public Response listar(@QueryParam("ativo") Boolean ativo) {
    var resultados =
        ativo == null
            ? listarContas.executar(usuarioId())
            : listarContas.executar(usuarioId(), ativo);
    var resposta = resultados.stream().map(mapper::toResponse).toList();
    return Response.ok(resposta).build();
  }

  @PUT
  @Path("/{contaId}")
  public Response editar(@PathParam("contaId") UUID contaId, @Valid EditarContaRequest request) {
    var resultado = editarConta.executar(mapper.toCommand(usuarioId(), contaId, request));
    return Response.ok(mapper.toResponse(resultado)).build();
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
