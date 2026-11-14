package com.joaovpg.economize.transferencia.http;

import com.joaovpg.economize.shared.exception.ValidacaoException;
import com.joaovpg.economize.shared.http.erro.LogHttpErrors;
import com.joaovpg.economize.transferencia.application.AlterarTransferencia;
import com.joaovpg.economize.transferencia.application.CriarTransferencia;
import com.joaovpg.economize.transferencia.application.ExcluirTransferencia;
import com.joaovpg.economize.transferencia.http.dto.request.AlterarTransferenciaRequest;
import com.joaovpg.economize.transferencia.http.dto.request.CriarTransferenciaRequest;
import com.joaovpg.economize.transferencia.http.dto.response.TransferenciaResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

@LogHttpErrors
public class TransferenciaResource implements TransferenciaResourceApi {
  @Context UriInfo uriInfo;

  private final CriarTransferencia criarTransferencia;
  private final AlterarTransferencia alterarTransferencia;
  private final ExcluirTransferencia excluirTransferencia;
  private final TransferenciaHttpMapper mapper;
  private final JsonWebToken token;

  TransferenciaResource(
      CriarTransferencia criarTransferencia,
      AlterarTransferencia alterarTransferencia,
      ExcluirTransferencia excluirTransferencia,
      TransferenciaHttpMapper mapper,
      JsonWebToken token) {
    this.criarTransferencia = criarTransferencia;
    this.alterarTransferencia = alterarTransferencia;
    this.excluirTransferencia = excluirTransferencia;
    this.mapper = mapper;
    this.token = token;
  }

  @Override
  public RestResponse<TransferenciaResponse> criar(CriarTransferenciaRequest request) {
    var resultado = criarTransferencia.executar(mapper.toCommand(usuarioId(), request));
    var location =
        uriInfo.getBaseUriBuilder().path("transferencias").path(resultado.id().toString()).build();
    return RestResponse.ResponseBuilder.create(
            RestResponse.Status.CREATED, mapper.toResponse(resultado))
        .location(location)
        .build();
  }

  @Override
  public TransferenciaResponse alterar(UUID id, AlterarTransferenciaRequest request) {
    var resultado = alterarTransferencia.executar(id, mapper.toCommand(usuarioId(), request));
    return mapper.toResponse(resultado);
  }

  @Override
  public RestResponse<Void> excluir(UUID id, String versaoEdicao) {
    if (versaoEdicao == null || versaoEdicao.isBlank()) {
      throw new ValidacaoException("versaoEdicao", "Versao de edicao obrigatoria para excluir");
    }
    excluirTransferencia.executar(usuarioId(), id, versaoEdicao);
    return RestResponse.noContent();
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
