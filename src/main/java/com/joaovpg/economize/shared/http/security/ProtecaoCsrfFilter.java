package com.joaovpg.economize.shared.http.security;

import com.joaovpg.economize.shared.exception.CsrfException;
import com.joaovpg.economize.usuario.http.CookiesAutenticacao;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class ProtecaoCsrfFilter implements ContainerRequestFilter {
  private static final Set<String> METODOS_MUTAVEIS = Set.of("POST", "PUT", "PATCH", "DELETE");
  private final boolean testCompatibility;

  public ProtecaoCsrfFilter(
      @ConfigProperty(name = "economize.auth.test-compatibility", defaultValue = "false")
          boolean testCompatibility) {
    this.testCompatibility = testCompatibility;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (!METODOS_MUTAVEIS.contains(requestContext.getMethod()) || endpointPublico(requestContext)) {
      return;
    }
    if (testCompatibility && "true".equals(requestContext.getHeaderString("X-Test-Auth"))) {
      return;
    }

    var cookies = requestContext.getCookies();
    Cookie csrfCookie = cookies.get(CookiesAutenticacao.CSRF_COOKIE);
    var csrfHeader = requestContext.getHeaderString(CookiesAutenticacao.CSRF_HEADER);
    if (csrfCookie == null
        || csrfHeader == null
        || csrfHeader.isBlank()
        || !csrfHeader.equals(csrfCookie.getValue())) {
      throw new CsrfException();
    }
  }

  private boolean endpointPublico(ContainerRequestContext requestContext) {
    var path = requestContext.getUriInfo().getPath();
    return path.endsWith("/autenticacao/cadastro")
        || path.endsWith("/autenticacao/login")
        || path.endsWith("/autenticacao/logout");
  }
}
