package com.joaovpg.economize.transacao.http;

import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.SerieRecorrencia;
import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
import com.joaovpg.economize.transacao.application.AlterarTransacao;
import com.joaovpg.economize.transacao.application.ConsultarTransacoes;
import com.joaovpg.economize.transacao.application.CriarTransacao;
import com.joaovpg.economize.transacao.http.dto.request.AlterarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.ConsultaTransacoesRequest;
import com.joaovpg.economize.transacao.http.dto.request.CriarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.RepeticaoTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse;
import java.time.DayOfWeek;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
interface TransacaoHttpMapper {
  CriarTransacao.Comando toCommand(UUID usuarioId, CriarTransacaoRequest request);

  @Mapping(target = "repeticaoInformada", expression = "java(request.repeticaoInformada())")
  @Mapping(target = "escopoInformado", expression = "java(request.escopoInformado())")
  @Mapping(target = "resolucaoInformada", expression = "java(request.resolucaoInformada())")
  AlterarTransacao.Comando toCommand(UUID usuarioId, AlterarTransacaoRequest request);

  ConsultarTransacoes.Comando toCommand(UUID usuarioId, ConsultaTransacoesRequest request);

  ConsultaTransacoesResponse toResponse(ConsultarTransacoes.Resultado resultado);

  ConsultaTransacoesResponse.ItemResponse toResponse(ConsultarTransacoes.Item item);

  ConsultaTransacoesResponse.SerieResponse toResponse(SerieRecorrencia serie);

  ConsultaTransacoesResponse.RepeticaoResponse toResponse(RepeticaoRecorrencia repeticao);

  ConsultaTransacoesResponse.TerminoResponse toResponse(RepeticaoRecorrencia.Termino termino);

  ConsultaTransacoesResponse.ParcelamentoResponse toResponse(
      RepeticaoRecorrencia.Parcelamento parcelamento);

  @ValueMappings({
    @ValueMapping(source = "DAILY", target = "DIARIA"),
    @ValueMapping(source = "WEEKLY", target = "SEMANAL"),
    @ValueMapping(source = "MONTHLY", target = "MENSAL"),
    @ValueMapping(source = "YEARLY", target = "ANUAL")
  })
  ConsultaTransacoesResponse.FrequenciaRepeticao toFrequencia(FrequenciaRecorrencia frequencia);

  @ValueMappings({
    @ValueMapping(source = "MONDAY", target = "SEGUNDA"),
    @ValueMapping(source = "TUESDAY", target = "TERCA"),
    @ValueMapping(source = "WEDNESDAY", target = "QUARTA"),
    @ValueMapping(source = "THURSDAY", target = "QUINTA"),
    @ValueMapping(source = "FRIDAY", target = "SEXTA"),
    @ValueMapping(source = "SATURDAY", target = "SABADO"),
    @ValueMapping(source = "SUNDAY", target = "DOMINGO")
  })
  ConsultaTransacoesResponse.DiaSemanaRepeticao toDiaSemana(DayOfWeek diaSemana);

  @ValueMappings({
    @ValueMapping(source = "DIARIA", target = "DAILY"),
    @ValueMapping(source = "SEMANAL", target = "WEEKLY"),
    @ValueMapping(source = "MENSAL", target = "MONTHLY"),
    @ValueMapping(source = "ANUAL", target = "YEARLY")
  })
  FrequenciaRecorrencia toFrequenciaRecorrencia(RepeticaoTransacaoRequest.Frequencia frequencia);

  @ValueMappings({
    @ValueMapping(source = "SEGUNDA", target = "MONDAY"),
    @ValueMapping(source = "TERCA", target = "TUESDAY"),
    @ValueMapping(source = "QUARTA", target = "WEDNESDAY"),
    @ValueMapping(source = "QUINTA", target = "THURSDAY"),
    @ValueMapping(source = "SEXTA", target = "FRIDAY"),
    @ValueMapping(source = "SABADO", target = "SATURDAY"),
    @ValueMapping(source = "DOMINGO", target = "SUNDAY")
  })
  DayOfWeek toDayOfWeek(RepeticaoTransacaoRequest.DiaSemana diaSemana);

  /** Converte a forma HTTP em configuração de domínio, sem decidir se ela é válida. */
  default RepeticaoRecorrencia toRepeticao(RepeticaoTransacaoRequest request) {
    if (request == null) {
      return null;
    }
    var termino = request.termino();
    var parcelamento = request.parcelamento();
    return new RepeticaoRecorrencia(
        toFrequenciaRecorrencia(request.frequencia()),
        request.intervalo(),
        request.diasSemana().stream().map(this::toDayOfWeek).toList(),
        request.diasMes(),
        request.politicaData(),
        termino == null
            ? null
            : new RepeticaoRecorrencia.Termino(
                termino.tipo(), termino.data(), termino.quantidade()),
        parcelamento == null
            ? null
            : new RepeticaoRecorrencia.Parcelamento(
                parcelamento.numeroPrimeiraParcela(), parcelamento.totalParcelas()));
  }
}
