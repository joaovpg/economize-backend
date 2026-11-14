package com.joaovpg.economize.shared.http.erro;

import com.joaovpg.economize.shared.exception.RegraNegocioException;
import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

@Provider
public class RegraNegocioMapper implements ExceptionMapper<RegraNegocioException> {
  @Context UriInfo uriInfo;

  @Override
  public Response toResponse(RegraNegocioException exception) {
    return HttpProblem.builder()
        .withType(URI.create("urn:economize:problem:" + exception.codigo()))
        .withTitle("Regra de negocio violada")
        .withStatus("VERSAO_DESATUALIZADA".equals(exception.codigo()) ? 409 : 422)
        .withDetail(exception.getMessage())
        .withInstance(uriInfo.getRequestUri())
        .build()
        .toResponse();
  }
}
