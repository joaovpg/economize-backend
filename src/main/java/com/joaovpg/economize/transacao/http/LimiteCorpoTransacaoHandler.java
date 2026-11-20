package com.joaovpg.economize.transacao.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.configuration.MemorySize;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Responde 400 DADOS_INVALIDOS, em vez do 413 do Quarkus, quando o corpo de uma requisição a
 * transações ou transferências excede {@code quarkus.http.limits.max-body-size}. Executa antes do
 * limite do Quarkus (ordem -2) e só para essas rotas. Corpos sem Content-Length continuam sendo
 * recusados pelo Quarkus com 413.
 */
@ApplicationScoped
public class LimiteCorpoTransacaoHandler {
  private static final int ORDEM_ANTES_DO_LIMITE_QUARKUS = -3;
  private static final Pattern ROTAS =
      Pattern.compile("^/api/(?:transacoes|transferencias)(?:/.*)?$");

  @ConfigProperty(name = "quarkus.http.limits.max-body-size")
  MemorySize limiteCorpo;

  @Inject ObjectMapper objectMapper;

  void registra(@Observes Router router) {
    router.route().order(ORDEM_ANTES_DO_LIMITE_QUARKUS).handler(this::rejeitaSeExcederLimite);
  }

  private void rejeitaSeExcederLimite(RoutingContext contexto) {
    var caminho = contexto.request().path();
    var tamanho = contexto.request().getHeader(HttpHeaders.CONTENT_LENGTH);
    if (!ROTAS.matcher(caminho).matches() || tamanho == null) {
      contexto.next();
      return;
    }
    long bytes;
    try {
      bytes = Long.parseLong(tamanho);
    } catch (NumberFormatException exception) {
      contexto.next();
      return;
    }
    if (bytes <= limiteCorpo.asLongValue()) {
      contexto.next();
      return;
    }
    responde400(contexto, caminho);
  }

  private void responde400(RoutingContext contexto, String caminho) {
    var problema =
        Map.of(
            "type", "urn:economize:problem:DADOS_INVALIDOS",
            "title", "Dados invalidos",
            "status", 400,
            "detail", "O corpo da requisicao excede o tamanho maximo permitido",
            "instance", caminho);
    String corpo;
    try {
      corpo = objectMapper.writeValueAsString(problema);
    } catch (Exception exception) {
      contexto.fail(exception);
      return;
    }
    contexto
        .response()
        .setStatusCode(400)
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/problem+json")
        .putHeader(HttpHeaders.CONNECTION, "close")
        .endHandler(fim -> contexto.request().connection().close())
        .end(corpo);
  }
}
