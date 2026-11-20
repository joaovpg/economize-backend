package com.joaovpg.economize.transacao.http;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkus.resteasy.reactive.jackson.runtime.mappers.BuiltinMismatchedInputExceptionMapper;
import jakarta.annotation.Priority;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Provider
@Priority(1)
public class EntradaTransacaoInvalidaMapper implements ExceptionMapper<MismatchedInputException> {
  @Context UriInfo uriInfo;

  @Override
  public Response toResponse(MismatchedInputException exception) {
    if (!uriInfo.getPath().matches("/?(?:api/)?(?:transacoes|transferencias)(?:/.*)?")) {
      return new BuiltinMismatchedInputExceptionMapper().toResponse(exception);
    }

    var problem =
        HttpProblem.builder()
            .withType(URI.create("urn:economize:problem:DADOS_INVALIDOS"))
            .withTitle("Dados invalidos")
            .withStatus(Response.Status.BAD_REQUEST)
            .withDetail("O corpo da requisicao contem dados invalidos")
            .withInstance(uriInfo.getRequestUri());

    var caminho = exception.getPath();

    if (!caminho.isEmpty()) {
      var campo = caminho.getLast().getFieldName();
      if (campo != null) {
        var errors = List.of(Map.of("field", campo, "detail", "Valor ausente ou invalido"));
        problem.with("errors", errors);
      }
    }

    return problem.build().toResponse();
  }
}
