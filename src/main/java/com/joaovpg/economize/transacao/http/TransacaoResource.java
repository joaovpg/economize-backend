package com.joaovpg.economize.transacao.http;

import com.joaovpg.economize.shared.exception.ValidacaoException;
import com.joaovpg.economize.shared.http.LogHttpErrors;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.application.AlterarEstaEAsProximas;
import com.joaovpg.economize.transacao.application.AlterarOcorrenciaSerie;
import com.joaovpg.economize.transacao.application.AlterarTransacao;
import com.joaovpg.economize.transacao.application.ConsultarTransacoes;
import com.joaovpg.economize.transacao.application.CriarTransacao;
import com.joaovpg.economize.transacao.application.ExcluirOcorrenciaSerie;
import com.joaovpg.economize.transacao.application.ExcluirTransacao;
import com.joaovpg.economize.transacao.application.PreverAlteracaoTransacao;
import com.joaovpg.economize.transacao.http.dto.request.AlterarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.ConsultaTransacoesRequest;
import com.joaovpg.economize.transacao.http.dto.request.CriarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.PreviaEdicaoTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse.ItemResponse;
import com.joaovpg.economize.transacao.http.dto.response.PreviaEdicaoTransacaoResponse;
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
  private static final String ESCOPO_ESTA_E_AS_PROXIMAS = "ESTA_E_AS_PROXIMAS";

  private final CriarTransacao criarTransacao;
  private final AlterarTransacao alterarTransacao;
  private final AlterarOcorrenciaSerie alterarOcorrenciaSerie;
  private final AlterarEstaEAsProximas alterarEstaEAsProximas;
  private final ExcluirTransacao excluirTransacao;
  private final ExcluirOcorrenciaSerie excluirOcorrenciaSerie;
  private final ConsultarTransacoes consultarTransacoes;
  private final PreverAlteracaoTransacao preverAlteracaoTransacao;
  private final TransacaoHttpMapper mapper;
  private final JsonWebToken token;

  TransacaoResource(
      CriarTransacao criarTransacao,
      AlterarTransacao alterarTransacao,
      AlterarOcorrenciaSerie alterarOcorrenciaSerie,
      AlterarEstaEAsProximas alterarEstaEAsProximas,
      ExcluirTransacao excluirTransacao,
      ExcluirOcorrenciaSerie excluirOcorrenciaSerie,
      ConsultarTransacoes consultarTransacoes,
      PreverAlteracaoTransacao preverAlteracaoTransacao,
      TransacaoHttpMapper mapper,
      JsonWebToken token) {
    this.criarTransacao = criarTransacao;
    this.alterarTransacao = alterarTransacao;
    this.alterarOcorrenciaSerie = alterarOcorrenciaSerie;
    this.alterarEstaEAsProximas = alterarEstaEAsProximas;
    this.excluirTransacao = excluirTransacao;
    this.excluirOcorrenciaSerie = excluirOcorrenciaSerie;
    this.consultarTransacoes = consultarTransacoes;
    this.preverAlteracaoTransacao = preverAlteracaoTransacao;
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
  public ItemResponse alterar(String chaveEdicao, AlterarTransacaoRequest request) {
    var usuarioId = UUID.fromString(token.getSubject());
    if (IdentidadeEdicaoTransacao.ehChaveDeOcorrencia(chaveEdicao)) {
      var chave = IdentidadeEdicaoTransacao.identificarOcorrencia(chaveEdicao);
      if (ESCOPO_ESTA_E_AS_PROXIMAS.equals(mapper.escopoTexto(request.escopo()))) {
        var comando = mapper.toCommandEstaEAsProximas(usuarioId, chave, request);
        return mapper.toResponse(alterarEstaEAsProximas.executar(comando));
      }
      var comando = mapper.toCommandOcorrencia(usuarioId, chave, request);
      return mapper.toResponse(alterarOcorrenciaSerie.executar(comando));
    }
    var id = IdentidadeEdicaoTransacao.identificar(chaveEdicao);
    var comando = mapper.toCommand(usuarioId, request);
    var resultado = alterarTransacao.executar(id, comando);
    return mapper.toResponse(resultado);
  }

  @Override
  public PreviaEdicaoTransacaoResponse previaEdicao(
      String chaveEdicao, PreviaEdicaoTransacaoRequest request) {
    var usuarioId = UUID.fromString(token.getSubject());
    var comando = mapper.toCommandPrevia(usuarioId, request);
    if (IdentidadeEdicaoTransacao.ehChaveDeOcorrencia(chaveEdicao)) {
      var chave = IdentidadeEdicaoTransacao.identificarOcorrencia(chaveEdicao);
      return mapper.toResponse(preverAlteracaoTransacao.executarOcorrencia(chave, comando));
    }
    var id = IdentidadeEdicaoTransacao.identificar(chaveEdicao);
    return mapper.toResponse(preverAlteracaoTransacao.executarSimples(id, comando));
  }

  @Override
  public RestResponse<ItemResponse> criar(CriarTransacaoRequest request) {
    var comando = mapper.toCommand(UUID.fromString(token.getSubject()), request);
    var resultado = criarTransacao.executar(comando);
    var response = mapper.toResponse(resultado);

    return RestResponse.ResponseBuilder.create(RestResponse.Status.CREATED, response)
        .location(java.net.URI.create("/transacoes/" + response.chaveEdicao()))
        .build();
  }

  @Override
  public RestResponse<Void> excluir(String chaveEdicao, String escopo) {
    var usuarioId = UUID.fromString(token.getSubject());
    if (IdentidadeEdicaoTransacao.ehChaveDeOcorrencia(chaveEdicao)) {
      var chave = IdentidadeEdicaoTransacao.identificarOcorrencia(chaveEdicao);
      excluirOcorrenciaSerie.executar(usuarioId, chave.segmentoId(), chave.dataOriginal(), escopo);
    } else {
      var id = IdentidadeEdicaoTransacao.identificar(chaveEdicao);
      excluirTransacao.executar(usuarioId, id, escopo);
    }
    return RestResponse.noContent();
  }
}
