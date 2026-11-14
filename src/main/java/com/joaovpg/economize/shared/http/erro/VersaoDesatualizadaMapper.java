package com.joaovpg.economize.shared.http.erro;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

/**
 * Perda de corrida de versão durante a gravação responde 409 VERSAO_DESATUALIZADA, sem dados
 * financeiros, em vez de erro interno.
 */
@Provider
public class VersaoDesatualizadaMapper implements ExceptionMapper<OptimisticLockException> {
  @Context UriInfo uriInfo;

  @Override
  public Response toResponse(OptimisticLockException exception) {
    return HttpProblem.builder()
        .withType(URI.create("urn:economize:problem:VERSAO_DESATUALIZADA"))
        .withTitle("Regra de negocio violada")
        .withStatus(409)
        .withDetail("O recurso foi alterado por outra operacao")
        .withInstance(uriInfo.getRequestUri())
        .build()
        .toResponse();
  }
}
