package com.joaovpg.economize.shared.http.erro;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import java.util.Map;

@Provider
@Priority(1)
public class ValidacaoMapper implements ExceptionMapper<ConstraintViolationException> {
  @Context UriInfo uriInfo;

  @Override
  public Response toResponse(ConstraintViolationException exception) {
    var errors =
        exception.getConstraintViolations().stream()
            .map(
                violacao -> {
                  var caminho = violacao.getPropertyPath().toString();
                  var separador = caminho.lastIndexOf('.');
                  var campo = separador < 0 ? caminho : caminho.substring(separador + 1);

                  return Map.of("field", campo, "detail", violacao.getMessage());
                })
            .toList();

    return HttpProblem.builder()
        .withType(URI.create("urn:economize:problem:DADOS_INVALIDOS"))
        .withTitle("Dados invalidos")
        .withStatus(Response.Status.BAD_REQUEST)
        .withDetail("Um ou mais campos sao invalidos")
        .withInstance(uriInfo.getRequestUri())
        .with("errors", errors)
        .build()
        .toResponse();
  }
}
