package com.joaovpg.economize.usuario.http;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import java.security.SecureRandom;
import java.util.Base64;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CookiesAutenticacao {
  public static final String TOKEN_COOKIE = "economize_token";
  public static final String CSRF_COOKIE = "economize_csrf";
  public static final String CSRF_HEADER = "X-CSRF-Token";
  public static final String TOKEN_COOKIE_PATH = "/api";
  public static final String CSRF_COOKIE_PATH = "/";

  private final SecureRandom secureRandom = new SecureRandom();
  private final int lifespan;
  private final boolean secure;
  private final NewCookie.SameSite sameSite;

  @Inject
  public CookiesAutenticacao(
      @ConfigProperty(name = "smallrye.jwt.new-token.lifespan", defaultValue = "900") int lifespan,
      @ConfigProperty(name = "economize.auth.cookie.secure", defaultValue = "false") boolean secure,
      @ConfigProperty(name = "economize.auth.cookie.same-site", defaultValue = "None")
          String sameSite) {
    this.lifespan = lifespan;
    this.secure = secure;
    this.sameSite = NewCookie.SameSite.valueOf(sameSite.strip().toUpperCase());
  }

  public Cookies criar(String token) {
    return new Cookies(
        cookie(TOKEN_COOKIE, token, true, lifespan, TOKEN_COOKIE_PATH),
        cookie(CSRF_COOKIE, csrfToken(), false, lifespan, CSRF_COOKIE_PATH));
  }

  public Cookies expirar() {
    return new Cookies(
        cookie(TOKEN_COOKIE, "", true, 0, TOKEN_COOKIE_PATH),
        cookie(CSRF_COOKIE, "", false, 0, CSRF_COOKIE_PATH));
  }

  private String csrfToken() {
    var bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private NewCookie cookie(
      String name, String value, boolean httpOnly, int maxAge, String path) {
    return new NewCookie.Builder(name)
        .value(value)
        .path(path)
        .maxAge(maxAge)
        .secure(secure)
        .httpOnly(httpOnly)
        .sameSite(sameSite)
        .build();
  }

  public record Cookies(NewCookie token, NewCookie csrf) {}
}
