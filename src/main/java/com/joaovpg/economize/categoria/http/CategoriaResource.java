package com.joaovpg.economize.categoria.http;

import com.joaovpg.economize.categoria.application.CadastrarCategoria;
import com.joaovpg.economize.categoria.application.EditarCategoria;
import com.joaovpg.economize.categoria.application.ListarCategorias;
import com.joaovpg.economize.categoria.http.dto.request.CadastrarCategoriaRequest;
import com.joaovpg.economize.categoria.http.dto.request.EditarCategoriaRequest;
import com.joaovpg.economize.categoria.http.dto.response.CategoriaResponse;
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

@Path("/categorias")
@LogHttpErrors
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("usuario")
public class CategoriaResource {
  private final CadastrarCategoria cadastrarCategoria;
  private final EditarCategoria editarCategoria;
  private final ListarCategorias listarCategorias;
  private final CategoriaHttpMapper mapper;
  private final JsonWebToken token;

  CategoriaResource(
      CadastrarCategoria cadastrarCategoria,
      EditarCategoria editarCategoria,
      ListarCategorias listarCategorias,
      CategoriaHttpMapper mapper,
      JsonWebToken token) {
    this.cadastrarCategoria = cadastrarCategoria;
    this.editarCategoria = editarCategoria;
    this.listarCategorias = listarCategorias;
    this.mapper = mapper;
    this.token = token;
  }

  @POST
  @APIResponseSchema(
      value = CategoriaResponse.class,
      responseCode = "201",
      responseDescription = "Categoria criada com sucesso.")
  @APIResponse(responseCode = "404", description = "Categoria pai não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public RestResponse<CategoriaResponse> cadastrar(@Valid CadastrarCategoriaRequest request) {
    var comando = mapper.toCommand(usuarioId(), request);
    var resultado = cadastrarCategoria.executar(comando);
    var resposta = mapper.toResponse(resultado);

    return RestResponse.status(RestResponse.Status.CREATED, resposta);
  }

  @PUT
  @Path("/{categoriaId}")
  @APIResponseSchema(
      value = CategoriaResponse.class,
      responseCode = "200",
      responseDescription = "Categoria atualizada com sucesso.")
  @APIResponse(responseCode = "404", description = "Categoria não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public CategoriaResponse editar(
      @PathParam("categoriaId") UUID categoriaId, @Valid EditarCategoriaRequest request) {
    var comando = mapper.toCommand(usuarioId(), categoriaId, request);
    var resultado = editarCategoria.executar(comando);
    return mapper.toResponse(resultado);
  }

  @GET
  public List<CategoriaResponse> listar(@QueryParam("ativo") Boolean ativo) {
    var resultado = listarCategorias.executar(usuarioId(), ativo);
    return mapper.toResponse(resultado);
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
