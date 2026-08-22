package com.joaovpg.economize.usuario.http;

import com.joaovpg.economize.usuario.application.AutenticarUsuario;
import com.joaovpg.economize.usuario.application.CadastrarUsuario;
import com.joaovpg.economize.usuario.http.dto.request.CadastroRequest;
import com.joaovpg.economize.usuario.http.dto.request.LoginRequest;
import com.joaovpg.economize.usuario.http.dto.response.UsuarioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
interface AutenticacaoHttpMapper {
  AutenticarUsuario.Comando toCommand(LoginRequest request);

  CadastrarUsuario.Comando toCommand(CadastroRequest request);

  UsuarioResponse toResponse(CadastrarUsuario.UsuarioCadastrado usuario);
}
