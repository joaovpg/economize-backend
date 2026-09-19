package com.joaovpg.economize.conta.http;

import com.joaovpg.economize.conta.http.dto.request.CadastrarContaRequest;
import com.joaovpg.economize.conta.http.dto.request.EditarContaRequest;
import com.joaovpg.economize.conta.http.dto.response.ContaResponse;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/contas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("usuario")
public interface ContaResourceApi {
  @POST
  @Tag(name = "Contas")
  @Operation(
      operationId = "cadastrarConta",
      summary = "Cadastra uma conta financeira",
      description =
          "Cria uma conta pertencente ao usuário autenticado. `saldoInicial` passa a compor o "
              + "saldo da conta a partir de `dataSaldoInicial`; a conta começa ativa. A API aceita "
              + "atualmente somente a moeda BRL.")
  @APIResponseSchema(
      value = ContaResponse.class,
      responseCode = "201",
      responseDescription = "Conta criada com sucesso.")
  @APIResponse(responseCode = "404", description = "Usuário autenticado não encontrado.")
  @APIResponse(
      responseCode = "422",
      description = "Regra de negócio violada, por exemplo moeda ou saldo inicial inválidos.")
  RestResponse<ContaResponse> cadastrar(
      @Valid @RequestBody(
              description = "Dados da conta financeira.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = CadastrarContaRequest.class),
                      examples =
                          @ExampleObject(
                              name = "contaComSaldoInicial",
                              value =
                                  "{\"nome\":\"Conta corrente\",\"moeda\":\"BRL\","
                                      + "\"saldoInicial\":1500.00,\"dataSaldoInicial\":\"2026-01-01\"}")))
          CadastrarContaRequest request);

  @GET
  @Tag(name = "Contas")
  @Operation(
      operationId = "listarContas",
      summary = "Lista as contas financeiras",
      description =
          "Retorna somente contas do usuário autenticado. Sem `ativo`, lista contas ativas e "
              + "inativas; com `ativo=true` ou `ativo=false`, aplica o filtro informado.")
  @APIResponse(
      responseCode = "200",
      description = "Contas consultadas com sucesso.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(type = SchemaType.ARRAY, implementation = ContaResponse.class)))
  List<ContaResponse> listar(
      @QueryParam("ativo")
          @Parameter(
              name = "ativo",
              in = ParameterIn.QUERY,
              description = "Filtra pelo estado ativo da conta.",
              required = false,
              schema = @Schema(implementation = Boolean.class))
          Boolean ativo);

  @PUT
  @Path("/{contaId}")
  @Tag(name = "Contas")
  @Operation(
      operationId = "editarConta",
      summary = "Edita uma conta financeira",
      description =
          "Atualiza os dados da conta do usuário autenticado. O identificador de uma conta de "
              + "outro usuário é tratado como recurso não encontrado.")
  @APIResponseSchema(
      value = ContaResponse.class,
      responseCode = "200",
      responseDescription = "Conta atualizada com sucesso.")
  @APIResponse(responseCode = "404", description = "Conta não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  ContaResponse editar(
      @PathParam("contaId")
          @Parameter(
              name = "contaId",
              in = ParameterIn.PATH,
              description = "Identificador da conta financeira.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID contaId,
      @Valid @RequestBody(
              description = "Novos dados da conta financeira.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = EditarContaRequest.class)))
          EditarContaRequest request);
}
