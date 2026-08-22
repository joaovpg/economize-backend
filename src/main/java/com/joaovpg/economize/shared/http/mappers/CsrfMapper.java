package com.joaovpg.economize.shared.http.mappers;

import com.joaovpg.economize.shared.exception.CsrfException;
import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

@Provider
public class CsrfMapper implements ExceptionMapper<CsrfException> {
  @Context UriInfo uriInfo;

  @Override
  public Response toResponse(CsrfException exception) {
    return HttpProblem.builder()
        .withType(URI.create("urn:economize:problem:CSRF_TOKEN_INVALIDO"))
        .withTitle("Falha de proteção CSRF")
        .withStatus(Response.Status.FORBIDDEN)
        .withDetail(exception.getMessage())
        .withInstance(uriInfo.getRequestUri())
        .build()
        .toResponse();
  }
}
