package com.joaovpg.economize.recorrencia.http;

import com.joaovpg.economize.recorrencia.application.CriarParcelamento;
import com.joaovpg.economize.recorrencia.application.CriarRecorrencia;
import com.joaovpg.economize.recorrencia.application.GerenciarOcorrenciaRecorrente;
import com.joaovpg.economize.recorrencia.enums.EscopoOcorrencia;
import com.joaovpg.economize.recorrencia.http.dto.request.AlterarOcorrenciaRecorrenteRequest;
import com.joaovpg.economize.recorrencia.http.dto.request.CriarRecorrenciaRequest;
import com.joaovpg.economize.recorrencia.http.dto.request.EfetivarOcorrenciaRecorrenteRequest;
import com.joaovpg.economize.recorrencia.http.dto.response.RecorrenciaOperacaoResponse;
import com.joaovpg.economize.recorrencia.http.dto.response.RecorrenciaResponse;
import com.joaovpg.economize.shared.http.LogHttpErrors;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

@LogHttpErrors
public class RecorrenciaResource implements RecorrenciaResourceApi {
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

  @Override
  public RestResponse<RecorrenciaResponse> criar(CriarRecorrenciaRequest request) {
    return switch (request.tipoGrupo()) {
      case RECORRENCIA -> {
        var resultado =
            criarRecorrencia.executar(mapper.toRecorrenciaCommand(usuarioId(), request));
        yield RestResponse.status(RestResponse.Status.CREATED, mapper.toResponse(resultado));
      }
      case PARCELAMENTO -> {
        var resultado =
            criarParcelamento.executar(mapper.toParcelamentoCommand(usuarioId(), request));
        yield RestResponse.status(RestResponse.Status.CREATED, mapper.toResponse(resultado));
      }
    };
  }

  @Override
  public RecorrenciaOperacaoResponse editar(
      UUID segmentoId,
      java.time.LocalDate dataOriginal,
      AlterarOcorrenciaRecorrenteRequest request) {
    var resultado =
        gerenciarOcorrencia.editar(
            mapper.toCommand(usuarioId(), segmentoId, dataOriginal, request));
    return mapper.toResponse(resultado);
  }

  @Override
  public RecorrenciaOperacaoResponse efetivar(
      UUID segmentoId,
      java.time.LocalDate dataOriginal,
      EfetivarOcorrenciaRecorrenteRequest request) {
    var comando =
        mapper.toCommand(
            usuarioId(),
            segmentoId,
            dataOriginal,
            request == null ? new EfetivarOcorrenciaRecorrenteRequest(null) : request);
    var resultado = gerenciarOcorrencia.efetivar(comando);
    return mapper.toResponse(resultado);
  }

  @Override
  public RestResponse<Void> excluir(
      UUID segmentoId, java.time.LocalDate dataOriginal, EscopoOcorrencia escopo) {
    gerenciarOcorrencia.excluir(usuarioId(), segmentoId, dataOriginal, escopo);
    return RestResponse.noContent();
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
