package com.joaovpg.economize.transferencia.http;

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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/transferencias")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("usuario")
public interface TransferenciaResourceApi {
  @POST
  @Tag(name = "Transferências")
  @Operation(
      operationId = "criarTransferencia",
      summary = "Cria uma transferência entre contas",
      description =
          "Cria uma operação que movimenta o mesmo valor como despesa na conta de origem e "
              + "receita na conta de destino. As contas devem ser diferentes, ativas, pertencer "
              + "ao usuário e usar a mesma moeda. Uma transferência `EFETIVADA` não pode ter data "
              + "financeira futura.")
  @APIResponseSchema(
      value = TransferenciaResponse.class,
      responseCode = "201",
      responseDescription = "Transferência criada com sucesso.")
  @APIResponse(responseCode = "404", description = "Conta de origem ou destino não encontrada.")
  @APIResponse(
      responseCode = "422",
      description =
          "Regra violada; exemplos: CONTAS_TRANSFERENCIA_IGUAIS, "
              + "MOEDAS_TRANSFERENCIA_DIFERENTES ou DATA_FINANCEIRA_FUTURA.")
  RestResponse<TransferenciaResponse> criar(
      @Valid @RequestBody(
              description = "Dados da transferência.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = CriarTransferenciaRequest.class),
                      examples =
                          @ExampleObject(
                              name = "transferenciaPlanejada",
                              value =
                                  "{\"contaOrigemId\":\"00000000-0000-0000-0000-000000000001\","
                                      + "\"contaDestinoId\":\"00000000-0000-0000-0000-000000000002\","
                                      + "\"situacao\":\"PLANEJADA\",\"descricao\":\"Reserva\","
                                      + "\"valor\":500.00,\"dataFinanceira\":\"2026-03-01\"}")))
          CriarTransferenciaRequest request);

  @PUT
  @Path("/{id}")
  @Tag(name = "Transferências")
  @Operation(
      operationId = "alterarTransferencia",
      summary = "Altera uma transferência",
      description =
          "Atualiza a transferência e os dois lados financeiros da operação. Mantém as regras de "
              + "contas diferentes, mesma moeda e data financeira compatível com o saldo inicial; "
              + "ao mudar para `PLANEJADA`, `efetivadoEm` é limpo.")
  @APIResponseSchema(
      value = TransferenciaResponse.class,
      responseCode = "200",
      responseDescription = "Transferência atualizada com sucesso.")
  @APIResponse(
      responseCode = "404",
      description = "Transferência ou conta relacionada não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  TransferenciaResponse alterar(
      @PathParam("id")
          @Parameter(
              name = "id",
              in = ParameterIn.PATH,
              description = "Identificador da transferência.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID id,
      @Valid @RequestBody(
              description = "Novos dados da transferência.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = AlterarTransferenciaRequest.class)))
          AlterarTransferenciaRequest request);

  @DELETE
  @Path("/{id}")
  @Tag(name = "Transferências")
  @Operation(
      operationId = "excluirTransferencia",
      summary = "Exclui uma transferência",
      description =
          "Exclui a transferência do usuário autenticado e as duas transações internas que a "
              + "representam no extrato.")
  @APIResponse(responseCode = "204", description = "Transferência excluída com sucesso.")
  @APIResponse(responseCode = "404", description = "Transferência não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  RestResponse<Void> excluir(
      @PathParam("id")
          @Parameter(
              name = "id",
              in = ParameterIn.PATH,
              description = "Identificador da transferência.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID id);
}
