package com.joaovpg.economize.transacao.http;

import com.joaovpg.economize.shared.exception.ValidacaoException;
import com.joaovpg.economize.shared.http.LogHttpErrors;
import com.joaovpg.economize.transacao.application.AlterarTransacao;
import com.joaovpg.economize.transacao.application.ConsultarTransacoes;
import com.joaovpg.economize.transacao.application.CriarTransacao;
import com.joaovpg.economize.transacao.application.ExcluirTransacao;
import com.joaovpg.economize.transacao.http.dto.request.AlterarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.ConsultaTransacoesRequest;
import com.joaovpg.economize.transacao.http.dto.request.CriarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse;
import com.joaovpg.economize.transacao.http.dto.response.TransacaoResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.enums.Explode;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterStyle;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/transacoes")
@LogHttpErrors
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TransacaoResource {
  private final CriarTransacao criarTransacao;
  private final AlterarTransacao alterarTransacao;
  private final ExcluirTransacao excluirTransacao;
  private final ConsultarTransacoes consultarTransacoes;
  private final TransacaoHttpMapper mapper;
  private final JsonWebToken token;

  TransacaoResource(
      CriarTransacao criarTransacao,
      AlterarTransacao alterarTransacao,
      ExcluirTransacao excluirTransacao,
      ConsultarTransacoes consultarTransacoes,
      TransacaoHttpMapper mapper,
      JsonWebToken token) {
    this.criarTransacao = criarTransacao;
    this.alterarTransacao = alterarTransacao;
    this.excluirTransacao = excluirTransacao;
    this.consultarTransacoes = consultarTransacoes;
    this.mapper = mapper;
    this.token = token;
  }

  @GET
  @RolesAllowed("usuario")
  @APIResponseSchema(
      value = ConsultaTransacoesResponse.class,
      responseCode = "200",
      responseDescription = "Transações consultadas com sucesso.")
  @APIResponse(responseCode = "404", description = "Conta ou categoria não encontrada.")
  public ConsultaTransacoesResponse consultar(
      @QueryParam("inicio")
          @Parameter(
              name = "inicio",
              in = ParameterIn.QUERY,
              description = "Mês inicial no formato AAAA-MM.",
              required = true,
              schema = @Schema(type = SchemaType.STRING, pattern = "\\d{4}-(0[1-9]|1[0-2])"))
          String inicio,
      @QueryParam("fim")
          @Parameter(
              name = "fim",
              in = ParameterIn.QUERY,
              description = "Mês final no formato AAAA-MM.",
              required = true,
              schema = @Schema(type = SchemaType.STRING, pattern = "\\d{4}-(0[1-9]|1[0-2])"))
          String fim,
      @QueryParam("contaId")
          @Parameter(
              name = "contaId",
              in = ParameterIn.QUERY,
              description = "Pode ser repetido para filtrar por várias contas.",
              style = ParameterStyle.FORM,
              explode = Explode.TRUE,
              schema = @Schema(type = SchemaType.ARRAY, implementation = UUID.class))
          List<String> contaIds,
      @QueryParam("categoriaId")
          @Parameter(
              name = "categoriaId",
              in = ParameterIn.QUERY,
              description = "Pode ser repetido para filtrar por várias categorias.",
              style = ParameterStyle.FORM,
              explode = Explode.TRUE,
              schema = @Schema(type = SchemaType.ARRAY, implementation = UUID.class))
          List<String> categoriaIds) {
    var request =
        new ConsultaTransacoesRequest(
            converterOpcional(inicio, YearMonth::parse, "inicio"),
            converterOpcional(fim, YearMonth::parse, "fim"),
            converterLista(contaIds, UUID::fromString, "contaId"),
            converterLista(categoriaIds, UUID::fromString, "categoriaId"));
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    return mapper.toResponse(consultarTransacoes.executar(comando));
  }

  private <T> T converterOpcional(String valor, Function<String, T> conversor, String campo) {
    if (valor == null) {
      return null;
    }
    try {
      return conversor.apply(valor);
    } catch (IllegalArgumentException | DateTimeException exception) {
      throw new ValidacaoException(campo, "Valor invalido");
    }
  }

  private <T> List<T> converterLista(
      List<String> valores, Function<String, T> conversor, String campo) {
    var convertidos = new ArrayList<T>();
    for (var valor : valores) {
      convertidos.add(converterOpcional(valor, conversor, campo));
    }
    return convertidos;
  }

  @PUT
  @Path("/{id}")
  @RolesAllowed("usuario")
  @APIResponseSchema(
      value = TransacaoResponse.class,
      responseCode = "200",
      responseDescription = "Transação atualizada com sucesso.")
  @APIResponse(
      responseCode = "404",
      description = "Transação ou recurso relacionado não encontrado.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public TransacaoResponse alterar(
      @PathParam("id") UUID id, @Valid AlterarTransacaoRequest request) {
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    var resultado = alterarTransacao.executar(id, comando);
    return mapper.toResponse(resultado);
  }

  @POST
  @RolesAllowed("usuario")
  @APIResponseSchema(
      value = TransacaoResponse.class,
      responseCode = "201",
      responseDescription = "Transação criada com sucesso.")
  @APIResponse(responseCode = "404", description = "Recurso relacionado não encontrado.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public RestResponse<TransacaoResponse> criar(@Valid CriarTransacaoRequest request) {
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    var resultado = criarTransacao.executar(comando);
    var response = mapper.toResponse(resultado);
    return RestResponse.status(RestResponse.Status.CREATED, response);
  }

  @DELETE
  @Path("/{id}")
  @RolesAllowed("usuario")
  @APIResponse(responseCode = "204", description = "Transação excluída com sucesso.")
  @APIResponse(responseCode = "404", description = "Transação não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  public RestResponse<Void> excluir(@PathParam("id") UUID id) {
    excluirTransacao.executar(UUID.fromString(token.getSubject()), id);
    return RestResponse.noContent();
  }
}
