package com.joaovpg.economize.transferencia.http;

import com.joaovpg.economize.shared.http.LogHttpErrors;
import com.joaovpg.economize.transferencia.application.AlterarTransferencia;
import com.joaovpg.economize.transferencia.application.CriarTransferencia;
import com.joaovpg.economize.transferencia.application.ExcluirTransferencia;
import com.joaovpg.economize.transferencia.http.dto.request.AlterarTransferenciaRequest;
import com.joaovpg.economize.transferencia.http.dto.request.CriarTransferenciaRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/transferencias")
@LogHttpErrors
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("usuario")
public class TransferenciaResource {
  private final CriarTransferencia criarTransferencia;
  private final AlterarTransferencia alterarTransferencia;
  private final ExcluirTransferencia excluirTransferencia;
  private final TransferenciaHttpMapper mapper;
  private final JsonWebToken token;

  TransferenciaResource(
      CriarTransferencia criarTransferencia,
      AlterarTransferencia alterarTransferencia,
      ExcluirTransferencia excluirTransferencia,
      TransferenciaHttpMapper mapper,
      JsonWebToken token) {
    this.criarTransferencia = criarTransferencia;
    this.alterarTransferencia = alterarTransferencia;
    this.excluirTransferencia = excluirTransferencia;
    this.mapper = mapper;
    this.token = token;
  }

  @POST
  public Response criar(@Valid CriarTransferenciaRequest request) {
    var resultado = criarTransferencia.executar(mapper.toCommand(usuarioId(), request));
    return Response.status(Response.Status.CREATED).entity(mapper.toResponse(resultado)).build();
  }

  @PUT
  @Path("/{id}")
  public Response alterar(@PathParam("id") UUID id, @Valid AlterarTransferenciaRequest request) {
    var resultado = alterarTransferencia.executar(id, mapper.toCommand(usuarioId(), request));
    return Response.ok(mapper.toResponse(resultado)).build();
  }

  @DELETE
  @Path("/{id}")
  public Response excluir(@PathParam("id") UUID id) {
    excluirTransferencia.executar(usuarioId(), id);
    return Response.noContent().build();
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
