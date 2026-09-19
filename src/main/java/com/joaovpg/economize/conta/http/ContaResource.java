package com.joaovpg.economize.conta.http;

import com.joaovpg.economize.conta.application.CadastrarConta;
import com.joaovpg.economize.conta.application.EditarConta;
import com.joaovpg.economize.conta.application.ListarContas;
import com.joaovpg.economize.conta.http.dto.request.CadastrarContaRequest;
import com.joaovpg.economize.conta.http.dto.request.EditarContaRequest;
import com.joaovpg.economize.conta.http.dto.response.ContaResponse;
import com.joaovpg.economize.shared.http.LogHttpErrors;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

@LogHttpErrors
public class ContaResource implements ContaResourceApi {
  private final CadastrarConta cadastrarConta;
  private final EditarConta editarConta;
  private final ListarContas listarContas;
  private final ContaHttpMapper mapper;
  private final JsonWebToken token;

  ContaResource(
      CadastrarConta cadastrarConta,
      EditarConta editarConta,
      ListarContas listarContas,
      ContaHttpMapper mapper,
      JsonWebToken token) {
    this.cadastrarConta = cadastrarConta;
    this.editarConta = editarConta;
    this.listarContas = listarContas;
    this.mapper = mapper;
    this.token = token;
  }

  @Override
  public RestResponse<ContaResponse> cadastrar(CadastrarContaRequest request) {
    var resultado = cadastrarConta.executar(mapper.toCommand(usuarioId(), request));
    return RestResponse.status(RestResponse.Status.CREATED, mapper.toResponse(resultado));
  }

  @Override
  public List<ContaResponse> listar(Boolean ativo) {
    var resultados =
        ativo == null
            ? listarContas.executar(usuarioId())
            : listarContas.executar(usuarioId(), ativo);
    return resultados.stream().map(mapper::toResponse).toList();
  }

  @Override
  public ContaResponse editar(UUID contaId, EditarContaRequest request) {
    var resultado = editarConta.executar(mapper.toCommand(usuarioId(), contaId, request));
    return mapper.toResponse(resultado);
  }

  private UUID usuarioId() {
    return UUID.fromString(token.getSubject());
  }
}
