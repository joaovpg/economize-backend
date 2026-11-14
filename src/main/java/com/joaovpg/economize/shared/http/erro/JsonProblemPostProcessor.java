package com.joaovpg.economize.shared.http.erro;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import io.quarkiverse.httpproblem.postprocessing.ProblemPostProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class JsonProblemPostProcessor implements ProblemPostProcessor {

  @Override
  public HttpProblem apply(HttpProblem problem, ProblemContext context) {
    if (ehErroDeDadosDeTransacao(problem)) {
      return dadosInvalidos(problem);
    }
    if (ehJsonMalformado(problem)) {
      return jsonMalformado(problem);
    }
    return problem;
  }

  private static boolean ehErroDeDadosDeTransacao(HttpProblem problem) {
    URI instance = problem.getInstance();
    URI type = problem.getType();

    return problem.getStatusCode() == 400
        && instance != null
        && (instance.getPath().startsWith("/transacoes")
            || instance.getPath().startsWith("/transferencias"))
        && (type == null || "about:blank".equals(type.toString()));
  }

  private static boolean ehJsonMalformado(HttpProblem problem) {
    return problem.getStatusCode() == 400
        && Objects.equals(problem.getTitle(), "Bad Request")
        && Objects.equals(problem.getDetail(), "HTTP 400 Bad Request");
  }

  private static HttpProblem dadosInvalidos(HttpProblem problem) {
    HttpProblem.Builder builder =
        HttpProblem.builder(problem)
            .withType(URI.create("urn:economize:problem:DADOS_INVALIDOS"))
            .withTitle("Dados invalidos")
            .withDetail("O corpo da requisicao contem dados invalidos");

    Object campo = campoComErro(problem);
    if (campo != null) {
      Map<String, String> erro =
          Map.of("field", campo.toString(), "detail", "Valor ausente ou invalido");
      builder.with("errors", List.of(erro));
    }

    return builder.build();
  }

  private static Object campoComErro(HttpProblem problem) {
    Map<String, Object> parametros = problem.getParameters();
    Object campo = parametros.get("field");
    return campo != null ? campo : parametros.get("attributeName");
  }

  private static HttpProblem jsonMalformado(HttpProblem problem) {
    return HttpProblem.builder(problem)
        .withType(URI.create("urn:economize:problem:JSON_MALFORMADO"))
        .withTitle("JSON invalido")
        .withDetail("O corpo da requisicao contem um JSON malformado")
        .build();
  }
}
