package com.joaovpg.economize.conta.http;

import com.joaovpg.economize.conta.application.CadastrarConta;
import com.joaovpg.economize.conta.application.EditarConta;
import com.joaovpg.economize.conta.application.ListarContas;
import com.joaovpg.economize.conta.http.dto.request.CadastrarContaRequest;
import com.joaovpg.economize.conta.http.dto.request.EditarContaRequest;
import com.joaovpg.economize.conta.http.dto.response.ContaResponse;
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
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.jboss.resteasy.reactive.RestResponse;

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
  @APIResponseSchema(
      value = ContaResponse.class,
      responseCode = "201",
      responseDescription = "Conta criada com sucesso.")
  @APIResponse(responseCode = "404", description = "Usuário não encontrado.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public RestResponse<ContaResponse> cadastrar(@Valid CadastrarContaRequest request) {
    var resultado = cadastrarConta.executar(mapper.toCommand(usuarioId(), request));
    return RestResponse.status(RestResponse.Status.CREATED, mapper.toResponse(resultado));
  }

  @GET
  public List<ContaResponse> listar(@QueryParam("ativo") Boolean ativo) {
    var resultados =
        ativo == null
            ? listarContas.executar(usuarioId())
            : listarContas.executar(usuarioId(), ativo);
    return resultados.stream().map(mapper::toResponse).toList();
  }

  @PUT
  @Path("/{contaId}")
  @APIResponseSchema(
      value = ContaResponse.class,
      responseCode = "200",
      responseDescription = "Conta atualizada com sucesso.")
  @APIResponse(responseCode = "404", description = "Conta não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public ContaResponse editar(
      @PathParam("contaId") UUID contaId, @Valid EditarContaRequest request) {
    var resultado = editarConta.executar(mapper.toCommand(usuarioId(), contaId, request));
    return mapper.toResponse(resultado);
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
