package com.joaovpg.economize.recorrencia.http;

import com.joaovpg.economize.recorrencia.application.CriarParcelamento;
import com.joaovpg.economize.recorrencia.application.CriarRecorrencia;
import com.joaovpg.economize.recorrencia.application.GerenciarOcorrenciaRecorrente;
import com.joaovpg.economize.recorrencia.enums.EscopoOcorrencia;
import com.joaovpg.economize.recorrencia.http.dto.request.AlterarOcorrenciaRecorrenteRequest;
import com.joaovpg.economize.recorrencia.http.dto.request.CriarRecorrenciaRequest;
import com.joaovpg.economize.recorrencia.http.dto.request.EfetivarOcorrenciaRecorrenteRequest;
import com.joaovpg.economize.shared.http.LogHttpErrors;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/recorrencias")
@LogHttpErrors
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("usuario")
public class RecorrenciaResource {
  private final CriarRecorrencia criarRecorrencia;
  private final CriarParcelamento criarParcelamento;
  private final GerenciarOcorrenciaRecorrente gerenciarOcorrencia;
  private final RecorrenciaHttpMapper mapper;
  private final JsonWebToken token;

  RecorrenciaResource(
      CriarRecorrencia criarRecorrencia,
      CriarParcelamento criarParcelamento,
      GerenciarOcorrenciaRecorrente gerenciarOcorrencia,
      RecorrenciaHttpMapper mapper,
      JsonWebToken token) {
    this.criarRecorrencia = criarRecorrencia;
    this.criarParcelamento = criarParcelamento;
    this.gerenciarOcorrencia = gerenciarOcorrencia;
    this.mapper = mapper;
    this.token = token;
  }

  @POST
  public Response criar(@Valid CriarRecorrenciaRequest request) {
    return switch (request.tipoGrupo()) {
      case RECORRENCIA -> {
        var resultado =
            criarRecorrencia.executar(mapper.toRecorrenciaCommand(usuarioId(), request));
        yield Response.status(Response.Status.CREATED).entity(mapper.toResponse(resultado)).build();
      }
      case PARCELAMENTO -> {
        var resultado =
            criarParcelamento.executar(mapper.toParcelamentoCommand(usuarioId(), request));
        yield Response.status(Response.Status.CREATED).entity(mapper.toResponse(resultado)).build();
      }
    };
  }

  @PUT
  @Path("/{segmentoId}/ocorrencias/{dataOriginal}")
  public Response editar(
      @PathParam("segmentoId") UUID segmentoId,
      @PathParam("dataOriginal") java.time.LocalDate dataOriginal,
      @Valid AlterarOcorrenciaRecorrenteRequest request) {
    var resultado =
        gerenciarOcorrencia.editar(
            mapper.toCommand(usuarioId(), segmentoId, dataOriginal, request));
    return Response.ok(mapper.toResponse(resultado)).build();
  }

  @POST
  @Path("/{segmentoId}/ocorrencias/{dataOriginal}/efetivar")
  public Response efetivar(
      @PathParam("segmentoId") UUID segmentoId,
      @PathParam("dataOriginal") java.time.LocalDate dataOriginal,
      EfetivarOcorrenciaRecorrenteRequest request) {
    var comando =
        mapper.toCommand(
            usuarioId(),
            segmentoId,
            dataOriginal,
            request == null ? new EfetivarOcorrenciaRecorrenteRequest(null) : request);
    var resultado = gerenciarOcorrencia.efetivar(comando);
    return Response.ok(mapper.toResponse(resultado)).build();
  }

  @DELETE
  @Path("/{segmentoId}/ocorrencias/{dataOriginal}")
  public Response excluir(
      @PathParam("segmentoId") UUID segmentoId,
      @PathParam("dataOriginal") java.time.LocalDate dataOriginal,
      @QueryParam("escopo") @DefaultValue("ONLY_THIS") EscopoOcorrencia escopo) {
    gerenciarOcorrencia.excluir(usuarioId(), segmentoId, dataOriginal, escopo);
    return Response.noContent().build();
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
