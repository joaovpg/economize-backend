package com.joaovpg.economize.usuario.http;

import com.joaovpg.economize.shared.http.LogHttpErrors;
import com.joaovpg.economize.usuario.application.AutenticarUsuario;
import com.joaovpg.economize.usuario.application.CadastrarUsuario;
import com.joaovpg.economize.usuario.http.dto.request.CadastroRequest;
import com.joaovpg.economize.usuario.http.dto.request.LoginRequest;
import com.joaovpg.economize.usuario.http.dto.response.CsrfTokenResponse;
import com.joaovpg.economize.usuario.http.dto.response.UsuarioResponse;
import org.jboss.resteasy.reactive.RestResponse;

@LogHttpErrors
public class AutenticacaoResource implements AutenticacaoResourceApi {
  private final AutenticarUsuario autenticarUsuario;
  private final CadastrarUsuario cadastrarUsuario;
  private final AutenticacaoHttpMapper mapper;
  private final CookiesAutenticacao cookies;

  AutenticacaoResource(
      AutenticarUsuario autenticarUsuario,
      CadastrarUsuario cadastrarUsuario,
      AutenticacaoHttpMapper mapper,
      CookiesAutenticacao cookies) {
    this.autenticarUsuario = autenticarUsuario;
    this.cadastrarUsuario = cadastrarUsuario;
    this.mapper = mapper;
    this.cookies = cookies;
  }

  @Override
  public RestResponse<UsuarioResponse> cadastrar(CadastroRequest request) {
    var comando = mapper.toCommand(request);
    var resultado = cadastrarUsuario.executar(comando);
    var resposta = mapper.toResponse(resultado.usuario());
    var cookiesSessao = cookies.criar(resultado.token());
    return RestResponse.ResponseBuilder.<UsuarioResponse>create(RestResponse.Status.CREATED)
        .entity(resposta)
        .cookie(cookiesSessao.token(), cookiesSessao.csrf())
        .build();
  }

  @Override
  public RestResponse<CsrfTokenResponse> login(LoginRequest request) {
    var comando = mapper.toCommand(request);
    var resultado = autenticarUsuario.executar(comando);
    var cookiesSessao = cookies.criar(resultado.token());
    var resposta = new CsrfTokenResponse(cookiesSessao.csrf().getValue());

    return RestResponse.ResponseBuilder.ok(resposta)
        .cookie(cookiesSessao.token(), cookiesSessao.csrf())
        .build();
  }

  @Override
  public RestResponse<Void> logout() {
    var cookiesExpirados = cookies.expirar();
    return RestResponse.ResponseBuilder.<Void>noContent()
        .cookie(cookiesExpirados.token(), cookiesExpirados.csrf())
        .build();
  }
}
