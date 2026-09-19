package com.joaovpg.economize.categoria.http;

import com.joaovpg.economize.categoria.application.CadastrarCategoria;
import com.joaovpg.economize.categoria.application.EditarCategoria;
import com.joaovpg.economize.categoria.application.ListarCategorias;
import com.joaovpg.economize.categoria.http.dto.request.CadastrarCategoriaRequest;
import com.joaovpg.economize.categoria.http.dto.request.EditarCategoriaRequest;
import com.joaovpg.economize.categoria.http.dto.response.CategoriaResponse;
import com.joaovpg.economize.shared.http.LogHttpErrors;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

@LogHttpErrors
public class CategoriaResource implements CategoriaResourceApi {
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

  @Override
  public RestResponse<CategoriaResponse> cadastrar(CadastrarCategoriaRequest request) {
    var comando = mapper.toCommand(usuarioId(), request);
    var resultado = cadastrarCategoria.executar(comando);
    var resposta = mapper.toResponse(resultado);

    return RestResponse.status(RestResponse.Status.CREATED, resposta);
  }

  @Override
  public CategoriaResponse editar(UUID categoriaId, EditarCategoriaRequest request) {
    var comando = mapper.toCommand(usuarioId(), categoriaId, request);
    var resultado = editarCategoria.executar(comando);
    return mapper.toResponse(resultado);
  }

  @Override
  public List<CategoriaResponse> listar(Boolean ativo) {
    var resultado = listarCategorias.executar(usuarioId(), ativo);
    return mapper.toResponse(resultado);
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
