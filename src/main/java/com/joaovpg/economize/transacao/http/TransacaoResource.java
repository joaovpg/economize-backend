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
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestResponse;

@LogHttpErrors
public class TransacaoResource implements TransacaoResourceApi {
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

  @Override
  public ConsultaTransacoesResponse consultar(
      String inicio, String fim, List<String> contaIds, List<String> categoriaIds) {
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

  @Override
  public TransacaoResponse alterar(UUID id, AlterarTransacaoRequest request) {
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    var resultado = alterarTransacao.executar(id, comando);
    return mapper.toResponse(resultado);
  }

  @Override
  public RestResponse<TransacaoResponse> criar(CriarTransacaoRequest request) {
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    var resultado = criarTransacao.executar(comando);
    var response = mapper.toResponse(resultado);
    return RestResponse.status(RestResponse.Status.CREATED, response);
  }

  @Override
  public RestResponse<Void> excluir(UUID id) {
    excluirTransacao.executar(UUID.fromString(token.getSubject()), id);
    return RestResponse.noContent();
  }
}
