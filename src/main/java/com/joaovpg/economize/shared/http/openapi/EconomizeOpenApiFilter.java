package com.joaovpg.economize.shared.http.openapi;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;

@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class EconomizeOpenApiFilter implements OASFilter {
  private static final String AUTHENTICATION_SCHEME = "SecurityScheme";
  private static final String TOKEN_COOKIE = "economize_token";
  private static final String CSRF_HEADER = "X-CSRF-Token";
  private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";
  private static final String PROBLEM_SCHEMA = "#/components/schemas/HttpProblem";
  private static final String PROBLEM_ERROR_SCHEMA = "#/components/schemas/ProblemError";
  private static final String YEAR_MONTH_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";
  private static final Set<PathItem.HttpMethod> MUTATING_METHODS =
      Set.of(
          PathItem.HttpMethod.POST,
          PathItem.HttpMethod.PUT,
          PathItem.HttpMethod.PATCH,
          PathItem.HttpMethod.DELETE);

  @Override
  public void filterOpenAPI(OpenAPI openAPI) {
    if (openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
      return;
    }

    var components = components(openAPI);
    registerProblemSchemas(components);
    registerProblemResponses(components);
    registerCookieSecurity(components);
    registerYearMonthSchema(components);

    for (var pathItem : openAPI.getPaths().getPathItems().values()) {
      if (pathItem == null || pathItem.getOperations() == null) {
        continue;
      }

      for (var operationEntry : pathItem.getOperations().entrySet()) {
        var operation = operationEntry.getValue();
        if (operation == null) {
          continue;
        }
        normalizeCookieSecurity(operation);
        addProblemResponses(operation);
        addCsrfHeader(operationEntry.getKey(), operation);
      }
    }
  }

  private Components components(OpenAPI openAPI) {
    if (openAPI.getComponents() == null) {
      openAPI.setComponents(OASFactory.createComponents());
    }
    return openAPI.getComponents();
  }

  private void registerProblemSchemas(Components components) {
    var problemErrorSchema =
        OASFactory.createSchema()
            .addType(Schema.SchemaType.OBJECT)
            .addProperty("field", OASFactory.createSchema().addType(Schema.SchemaType.STRING))
            .addProperty("detail", OASFactory.createSchema().addType(Schema.SchemaType.STRING));
    components.addSchema("ProblemError", problemErrorSchema);

    var problemSchema =
        components.getSchemas() == null ? null : components.getSchemas().get("HttpProblem");
    if (problemSchema == null) {
      problemSchema = OASFactory.createSchema().addType(Schema.SchemaType.OBJECT);
      components.addSchema("HttpProblem", problemSchema);
    }
    problemSchema.addProperty(
        "errors",
        OASFactory.createSchema()
            .addType(Schema.SchemaType.ARRAY)
            .items(OASFactory.createSchema().ref(PROBLEM_ERROR_SCHEMA)));
  }

  private void registerProblemResponses(Components components) {
    components.addResponse(
        "BadRequestProblem", problemResponse("Dados inválidos.", PROBLEM_SCHEMA));
    components.addResponse(
        "UnauthorizedProblem", problemResponse("Autenticação necessária.", PROBLEM_SCHEMA));
    components.addResponse(
        "ForbiddenProblem", problemResponse("Operação não permitida.", PROBLEM_SCHEMA));
    components.addResponse(
        "NotFoundProblem", problemResponse("Recurso não encontrado.", PROBLEM_SCHEMA));
    components.addResponse(
        "UnprocessableEntityProblem", problemResponse("Regra de negócio violada.", PROBLEM_SCHEMA));
  }

  private void registerCookieSecurity(Components components) {
    components.addSecurityScheme(
        AUTHENTICATION_SCHEME,
        OASFactory.createSecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.COOKIE)
            .name(TOKEN_COOKIE)
            .description("Autenticação por cookie HttpOnly; o header Authorization não é aceito."));
  }

  private void registerYearMonthSchema(Components components) {
    components.addSchema(
        "YearMonth",
        OASFactory.createSchema()
            .addType(Schema.SchemaType.STRING)
            .pattern(YEAR_MONTH_PATTERN)
            .example("2026-02")
            .description("Mês no formato AAAA-MM."));
  }

  private org.eclipse.microprofile.openapi.models.responses.APIResponse problemResponse(
      String description, String schemaReference) {
    var content = OASFactory.createContent();
    var mediaType = OASFactory.createMediaType();
    mediaType.setSchema(OASFactory.createSchema().ref(schemaReference));
    content.addMediaType(PROBLEM_MEDIA_TYPE, mediaType);

    return OASFactory.createAPIResponse().description(description).content(content);
  }

  private void addProblemResponses(Operation operation) {
    addProblemResponse(operation, "400", "BadRequestProblem");

    if (secured(operation)) {
      addProblemResponse(operation, "401", "UnauthorizedProblem");
      addProblemResponse(operation, "403", "ForbiddenProblem");
    }

    replaceDeclaredProblemResponse(operation, "404", "NotFoundProblem");
    replaceDeclaredProblemResponse(operation, "422", "UnprocessableEntityProblem");
  }

  private void replaceDeclaredProblemResponse(
      Operation operation, String status, String componentName) {
    if (operation.getResponses() != null
        && operation.getResponses().getAPIResponse(status) != null) {
      addProblemResponse(operation, status, componentName);
    }
  }

  private void addProblemResponse(Operation operation, String status, String componentName) {
    var responses = operation.getResponses();
    if (responses == null) {
      responses = OASFactory.createAPIResponses();
      operation.setResponses(responses);
    }

    var existing = responses.getAPIResponse(status);
    if (existing != null && (existing.getRef() != null || existing.getContent() != null)) {
      return;
    }

    responses.addAPIResponse(
        status, OASFactory.createAPIResponse().ref("#/components/responses/" + componentName));
  }

  private void addCsrfHeader(PathItem.HttpMethod method, Operation operation) {
    if (!secured(operation) || !MUTATING_METHODS.contains(method)) {
      return;
    }

    if (operation.getParameters() != null
        && operation.getParameters().stream()
            .anyMatch(
                parameter ->
                    parameter != null
                        && parameter.getIn() == Parameter.In.HEADER
                        && CSRF_HEADER.equals(parameter.getName()))) {
      return;
    }

    operation.addParameter(
        OASFactory.createParameter()
            .name(CSRF_HEADER)
            .in(Parameter.In.HEADER)
            .description(
                "Obrigatório em requisições mutáveis cross-site; deve repetir o valor do cookie"
                    + " economize_csrf.")
            .required(false)
            .schema(OASFactory.createSchema().addType(Schema.SchemaType.STRING)));
  }

  private boolean secured(Operation operation) {
    return operation.getSecurity() != null && !operation.getSecurity().isEmpty();
  }

  private void normalizeCookieSecurity(Operation operation) {
    if (!secured(operation)) {
      return;
    }

    operation
        .getSecurity()
        .forEach(
            requirement -> {
              if (requirement != null && requirement.hasScheme(AUTHENTICATION_SCHEME)) {
                requirement.addScheme(AUTHENTICATION_SCHEME, List.of());
              }
            });
  }
}
