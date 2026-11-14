package com.joaovpg.economize.shared.http.erro;

import com.joaovpg.economize.shared.exception.ValidacaoException;
import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Provider
public class ValidacaoSemanticaMapper implements ExceptionMapper<ValidacaoException> {
  @Context UriInfo uriInfo;

  @Override
  public Response toResponse(ValidacaoException exception) {
    return HttpProblem.builder()
        .withType(URI.create("urn:economize:problem:DADOS_INVALIDOS"))
        .withTitle("Dados invalidos")
        .withStatus(Response.Status.BAD_REQUEST)
        .withDetail("Um ou mais campos sao invalidos")
        .withInstance(uriInfo.getRequestUri())
        .with(
            "errors", List.of(Map.of("field", exception.campo(), "detail", exception.getMessage())))
        .build()
        .toResponse();
  }
}
