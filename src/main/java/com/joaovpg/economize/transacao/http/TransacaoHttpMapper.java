package com.joaovpg.economize.transacao.http;

import com.joaovpg.economize.transacao.application.AlterarTransacao;
import com.joaovpg.economize.transacao.application.ConsultarTransacoes;
import com.joaovpg.economize.transacao.application.CriarTransacao;
import com.joaovpg.economize.transacao.http.dto.request.AlterarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.ConsultaTransacoesRequest;
import com.joaovpg.economize.transacao.http.dto.request.CriarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
interface TransacaoHttpMapper {
  @Mapping(target = "repeticaoInformada", expression = "java(request.repeticaoInformada())")
  CriarTransacao.Comando toCommand(UUID usuarioId, CriarTransacaoRequest request);

  @Mapping(target = "repeticaoInformada", expression = "java(request.repeticaoInformada())")
  @Mapping(target = "escopoInformado", expression = "java(request.escopoInformado())")
  @Mapping(target = "resolucaoInformada", expression = "java(request.resolucaoInformada())")
  AlterarTransacao.Comando toCommand(UUID usuarioId, AlterarTransacaoRequest request);

  ConsultarTransacoes.Comando toCommand(UUID usuarioId, ConsultaTransacoesRequest request);

  ConsultaTransacoesResponse toResponse(ConsultarTransacoes.Resultado resultado);

  ConsultaTransacoesResponse.ItemResponse toResponse(ConsultarTransacoes.Item item);
}
