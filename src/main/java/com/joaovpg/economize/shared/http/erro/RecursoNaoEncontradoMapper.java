package com.joaovpg.economize.shared.http.erro;

import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

@Provider
public class RecursoNaoEncontradoMapper implements ExceptionMapper<RecursoNaoEncontradoException> {
  @Context UriInfo uriInfo;

  @Override
  public Response toResponse(RecursoNaoEncontradoException exception) {
    return HttpProblem.builder()
        .withType(URI.create("urn:economize:problem:" + exception.codigo()))
        .withTitle("Recurso nao encontrado")
        .withStatus(Response.Status.NOT_FOUND)
        .withDetail(exception.getMessage())
        .withInstance(uriInfo.getRequestUri())
        .build()
        .toResponse();
  }
}
