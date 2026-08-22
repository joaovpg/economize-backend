package com.joaovpg.economize.shared.http.security;

import com.joaovpg.economize.usuario.http.CookiesAutenticacao;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MecanismoAutenticacaoCookie implements HttpAuthenticationMechanism {
  private final boolean testCompatibility;
  private final JWTParser jwtParser;

  @Inject
  public MecanismoAutenticacaoCookie(
      @ConfigProperty(name = "economize.auth.test-compatibility", defaultValue = "false")
          boolean testCompatibility,
      JWTParser jwtParser) {
    this.testCompatibility = testCompatibility;
    this.jwtParser = jwtParser;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    var cookie = context.request().getCookie(CookiesAutenticacao.TOKEN_COOKIE);
    var authorization = context.request().getHeader("Authorization");
    if (cookie == null
        && testCompatibility
        && authorization != null
        && authorization.startsWith("Bearer ")) {
      var token = authorization.substring("Bearer ".length());
      context.request().headers().set("X-Test-Auth", "true");
      context.request().headers().remove("Authorization");
      return authenticateToken(token);
    }
    // Do not allow an Authorization header to bypass the cookie-only contract.
    context.request().headers().remove("Authorization");
    if (cookie == null || cookie.getValue().isBlank()) {
      return Uni.createFrom().nullItem();
    }
    return authenticateToken(cookie.getValue());
  }

  private Uni<SecurityIdentity> authenticateToken(String token) {
    try {
      var jwt = jwtParser.parse(token);
      return Uni.createFrom()
          .item(
              QuarkusSecurityIdentity.builder()
                  .setPrincipal(jwt)
                  .addRoles(jwt.getGroups())
                  .build());
    } catch (ParseException exception) {
      return Uni.createFrom().failure(exception);
    }
  }

  @Override
  public Set<Class<? extends io.quarkus.security.identity.request.AuthenticationRequest>>
      getCredentialTypes() {
    return Set.of(TokenAuthenticationRequest.class);
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom().item(new ChallengeData(401));
  }

  @Override
  public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
    return Uni.createFrom()
        .item(
            new HttpCredentialTransport(
                HttpCredentialTransport.Type.COOKIE, CookiesAutenticacao.TOKEN_COOKIE));
  }

  @Override
  public int getPriority() {
    return 1100;
  }
}
