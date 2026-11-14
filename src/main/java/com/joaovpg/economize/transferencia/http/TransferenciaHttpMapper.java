package com.joaovpg.economize.transferencia.http;

import com.joaovpg.economize.transferencia.application.AlterarTransferencia;
import com.joaovpg.economize.transferencia.application.CriarTransferencia;
import com.joaovpg.economize.transferencia.application.TransferenciaResultado;
import com.joaovpg.economize.transferencia.http.dto.request.AlterarTransferenciaRequest;
import com.joaovpg.economize.transferencia.http.dto.request.CriarTransferenciaRequest;
import com.joaovpg.economize.transferencia.http.dto.response.TransferenciaResponse;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
interface TransferenciaHttpMapper {
  CriarTransferencia.Comando toCommand(UUID usuarioId, CriarTransferenciaRequest request);

  AlterarTransferencia.Comando toCommand(UUID usuarioId, AlterarTransferenciaRequest request);

  @Mapping(source = "id", target = "transferenciaId")
  TransferenciaResponse toResponse(TransferenciaResultado resultado);
}
