package com.joaovpg.economize.transacao.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.joaovpg.economize.recorrencia.domain.FrequenciaRecorrencia;
import com.joaovpg.economize.recorrencia.domain.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.domain.SerieRecorrencia;
import com.joaovpg.economize.transacao.application.AlterarEstaEAsProximas;
import com.joaovpg.economize.transacao.application.AlterarOcorrenciaSerie;
import com.joaovpg.economize.transacao.application.AlterarTransacao;
import com.joaovpg.economize.transacao.application.ConsultarTransacoes;
import com.joaovpg.economize.transacao.application.CriarTransacao;
import com.joaovpg.economize.transacao.domain.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.http.dto.request.AlterarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.ConsultaTransacoesRequest;
import com.joaovpg.economize.transacao.http.dto.request.CriarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.RepeticaoTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
interface TransacaoHttpMapper {
  CriarTransacao.Comando toCommand(UUID usuarioId, CriarTransacaoRequest request);

  @Mapping(target = "escopoInformado", expression = "java(request.escopoInformado())")
  AlterarTransacao.Comando toCommand(UUID usuarioId, AlterarTransacaoRequest request);

  /**
   * Converte a alteração de uma ocorrência de série. O escopo chega como texto para que o caso de
   * uso decida se é aplicável, inclusive quando ausente ou nulo.
   */
  default AlterarOcorrenciaSerie.Comando toCommandOcorrencia(
      UUID usuarioId,
      IdentidadeEdicaoTransacao.ChaveOcorrencia chave,
      AlterarTransacaoRequest request) {
    return new AlterarOcorrenciaSerie.Comando(
        usuarioId,
        chave.segmentoId(),
        chave.dataOriginal(),
        request.versaoEdicao(),
        escopoTexto(request.escopo()),
        request.contaId(),
        request.categoriaId(),
        request.situacao(),
        request.tipo(),
        request.descricao(),
        request.observacoes(),
        request.valor(),
        request.dataFinanceira(),
        toRepeticao(request.repeticao()));
  }

  /** Converte a alteração com ESTA_E_AS_PROXIMAS; as regras de corte são do caso de uso. */
  default AlterarEstaEAsProximas.Comando toCommandEstaEAsProximas(
      UUID usuarioId,
      IdentidadeEdicaoTransacao.ChaveOcorrencia chave,
      AlterarTransacaoRequest request) {
    return new AlterarEstaEAsProximas.Comando(
        usuarioId,
        chave.segmentoId(),
        chave.dataOriginal(),
        request.versaoEdicao(),
        request.contaId(),
        request.categoriaId(),
        request.situacao(),
        request.tipo(),
        request.descricao(),
        request.observacoes(),
        request.valor(),
        request.dataFinanceira(),
        toRepeticao(request.repeticao()));
  }

  default String escopoTexto(JsonNode escopo) {
    if (escopo == null) {
      return null;
    }
    return escopo.isTextual() ? escopo.asText() : escopo.toString();
  }

  ConsultarTransacoes.Comando toCommand(UUID usuarioId, ConsultaTransacoesRequest request);

  ConsultaTransacoesResponse toResponse(ConsultarTransacoes.Resultado resultado);

  @Mapping(target = "chaveItem", expression = "java(item.chaveItem())")
  ConsultaTransacoesResponse.ItemResponse toResponse(ConsultarTransacoes.Item item);

  ConsultaTransacoesResponse.SerieResponse toResponse(SerieRecorrencia serie);

  ConsultaTransacoesResponse.RepeticaoResponse toResponse(RepeticaoRecorrencia repeticao);

  ConsultaTransacoesResponse.TerminoResponse toResponse(RepeticaoRecorrencia.Termino termino);

  @ValueMappings({
    @ValueMapping(source = "DAILY", target = "DIARIA"),
    @ValueMapping(source = "WEEKLY", target = "SEMANAL"),
    @ValueMapping(source = "MONTHLY", target = "MENSAL"),
    @ValueMapping(source = "YEARLY", target = "ANUAL")
  })
  ConsultaTransacoesResponse.FrequenciaRepeticao toFrequencia(FrequenciaRecorrencia frequencia);

  @ValueMappings({
    @ValueMapping(source = "DIARIA", target = "DAILY"),
    @ValueMapping(source = "SEMANAL", target = "WEEKLY"),
    @ValueMapping(source = "MENSAL", target = "MONTHLY"),
    @ValueMapping(source = "ANUAL", target = "YEARLY")
  })
  FrequenciaRecorrencia toFrequenciaRecorrencia(RepeticaoTransacaoRequest.Frequencia frequencia);

  /** Converte a forma HTTP em configuração de domínio, sem decidir se ela é válida. */
  default RepeticaoRecorrencia toRepeticao(RepeticaoTransacaoRequest request) {
    if (request == null) {
      return null;
    }
    var termino = request.termino();
    return new RepeticaoRecorrencia(
        toFrequenciaRecorrencia(request.frequencia()),
        request.intervalo(),
        request.ancoragem(),
        null,
        new RepeticaoRecorrencia.Termino(
            termino.tipo(),
            termino.data(),
            termino.quantidade(),
            termino.numeroPrimeiraParcela(),
            termino.totalParcelas()));
  }
}
