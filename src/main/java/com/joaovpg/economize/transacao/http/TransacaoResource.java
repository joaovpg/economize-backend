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
import jakarta.ws.rs.core.Response;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.microprofile.jwt.JsonWebToken;

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
  public Response consultar(
      @QueryParam("inicio") String inicio,
      @QueryParam("fim") String fim,
      @QueryParam("contaId") List<String> contaIds,
      @QueryParam("categoriaId") List<String> categoriaIds) {
    var request =
        new ConsultaTransacoesRequest(
            converterOpcional(inicio, YearMonth::parse, "inicio"),
            converterOpcional(fim, YearMonth::parse, "fim"),
            converterLista(contaIds, UUID::fromString, "contaId"),
            converterLista(categoriaIds, UUID::fromString, "categoriaId"));
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    return Response.ok(mapper.toResponse(consultarTransacoes.executar(comando))).build();
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
  public Response alterar(@PathParam("id") UUID id, @Valid AlterarTransacaoRequest request) {
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    var resultado = alterarTransacao.executar(id, comando);
    return Response.ok(mapper.toResponse(resultado)).build();
  }

  @POST
  @RolesAllowed("usuario")
  public Response criar(@Valid CriarTransacaoRequest request) {
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    var resultado = criarTransacao.executar(comando);
    var response = mapper.toResponse(resultado);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @DELETE
  @Path("/{id}")
  @RolesAllowed("usuario")
  public Response excluir(@PathParam("id") UUID id) {
    excluirTransacao.executar(UUID.fromString(token.getSubject()), id);
    return Response.noContent().build();
  }
}
