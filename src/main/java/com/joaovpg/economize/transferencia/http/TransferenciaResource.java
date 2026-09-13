package com.joaovpg.economize.transferencia.http;

import com.joaovpg.economize.shared.http.LogHttpErrors;
import com.joaovpg.economize.transferencia.application.AlterarTransferencia;
import com.joaovpg.economize.transferencia.application.CriarTransferencia;
import com.joaovpg.economize.transferencia.application.ExcluirTransferencia;
import com.joaovpg.economize.transferencia.http.dto.request.AlterarTransferenciaRequest;
import com.joaovpg.economize.transferencia.http.dto.request.CriarTransferenciaRequest;
import com.joaovpg.economize.transferencia.http.dto.response.TransferenciaResponse;
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
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.jboss.resteasy.reactive.RestResponse;

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
  @APIResponseSchema(
      value = TransferenciaResponse.class,
      responseCode = "201",
      responseDescription = "Transferência criada com sucesso.")
  @APIResponse(responseCode = "404", description = "Recurso relacionado não encontrado.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public RestResponse<TransferenciaResponse> criar(@Valid CriarTransferenciaRequest request) {
    var resultado = criarTransferencia.executar(mapper.toCommand(usuarioId(), request));
    return RestResponse.status(RestResponse.Status.CREATED, mapper.toResponse(resultado));
  }

  @PUT
  @Path("/{id}")
  @APIResponseSchema(
      value = TransferenciaResponse.class,
      responseCode = "200",
      responseDescription = "Transferência atualizada com sucesso.")
  @APIResponse(
      responseCode = "404",
      description = "Transferência ou recurso relacionado não encontrado.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public TransferenciaResponse alterar(
      @PathParam("id") UUID id, @Valid AlterarTransferenciaRequest request) {
    var resultado = alterarTransferencia.executar(id, mapper.toCommand(usuarioId(), request));
    return mapper.toResponse(resultado);
  }

  @DELETE
  @Path("/{id}")
  @APIResponse(responseCode = "204", description = "Transferência excluída com sucesso.")
  @APIResponse(responseCode = "404", description = "Transferência não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public RestResponse<Void> excluir(@PathParam("id") UUID id) {
    excluirTransferencia.executar(usuarioId(), id);
    return RestResponse.noContent();
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
