package com.joaovpg.economize.categoria.http;

import com.joaovpg.economize.categoria.http.dto.request.CadastrarCategoriaRequest;
import com.joaovpg.economize.categoria.http.dto.request.EditarCategoriaRequest;
import com.joaovpg.economize.categoria.http.dto.response.CategoriaResponse;
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

@Path("/categorias")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("usuario")
public interface CategoriaResourceApi {
  @POST
  @Tag(name = "Categorias")
  @Operation(
      operationId = "cadastrarCategoria",
      summary = "Cadastra uma categoria",
      description =
          "Cria uma categoria para o usuário autenticado. `categoriaPaiId` é opcional e, quando "
              + "informado, deve apontar para uma categoria ativa do mesmo usuário.")
  @APIResponseSchema(
      value = CategoriaResponse.class,
      responseCode = "201",
      responseDescription = "Categoria criada com sucesso.")
  @APIResponse(responseCode = "404", description = "Categoria pai não encontrada.")
  @APIResponse(responseCode = "422", description = "Nome, cor ou hierarquia inválidos.")
  RestResponse<CategoriaResponse> cadastrar(
      @Valid
          @RequestBody(
              description = "Dados da categoria.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = CadastrarCategoriaRequest.class),
                      examples =
                          @ExampleObject(
                              name = "categoriaComCor",
                              value = "{\"nome\":\"Alimentação\",\"cor\":\"#E67E22\"}")))
          CadastrarCategoriaRequest request);

  @PUT
  @Path("/{categoriaId}")
  @Tag(name = "Categorias")
  @Operation(
      operationId = "editarCategoria",
      summary = "Edita uma categoria",
      description =
          "Atualiza uma categoria do usuário autenticado. A categoria pai pode ser removida "
              + "enviando `categoriaPaiId` como `null`.")
  @APIResponseSchema(
      value = CategoriaResponse.class,
      responseCode = "200",
      responseDescription = "Categoria atualizada com sucesso.")
  @APIResponse(responseCode = "404", description = "Categoria não encontrada.")
  @APIResponse(responseCode = "422", description = "Nome, cor ou hierarquia inválidos.")
  CategoriaResponse editar(
      @PathParam("categoriaId")
          @Parameter(
              name = "categoriaId",
              in = ParameterIn.PATH,
              description = "Identificador da categoria.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID categoriaId,
      @Valid
          @RequestBody(
              description = "Novos dados da categoria.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = EditarCategoriaRequest.class)))
          EditarCategoriaRequest request);

  @GET
  @Tag(name = "Categorias")
  @Operation(
      operationId = "listarCategorias",
      summary = "Lista as categorias",
      description =
          "Retorna as categorias do usuário autenticado. Sem `ativo`, o filtro é omitido; com "
              + "`ativo=true` ou `ativo=false`, retorna somente categorias no estado informado.")
  @APIResponse(
      responseCode = "200",
      description = "Categorias consultadas com sucesso.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(type = SchemaType.ARRAY, implementation = CategoriaResponse.class)))
  List<CategoriaResponse> listar(
      @QueryParam("ativo")
          @Parameter(
              name = "ativo",
              in = ParameterIn.QUERY,
              description = "Filtra pelo estado ativo da categoria.",
              schema = @Schema(implementation = Boolean.class))
          Boolean ativo);
}
