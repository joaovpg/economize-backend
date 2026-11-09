package com.joaovpg.economize.transacao.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.joaovpg.economize.recorrencia.RepeticaoRecorrencia;
import com.joaovpg.economize.recorrencia.SerieRecorrencia;
import com.joaovpg.economize.recorrencia.enums.FrequenciaRecorrencia;
import com.joaovpg.economize.shared.exception.ValidacaoException;
import com.joaovpg.economize.transacao.IdentidadeEdicaoTransacao;
import com.joaovpg.economize.transacao.ResolucaoSobreposicao;
import com.joaovpg.economize.transacao.application.AlterarEstaEAsProximas;
import com.joaovpg.economize.transacao.application.AlterarOcorrenciaSerie;
import com.joaovpg.economize.transacao.application.AlterarTransacao;
import com.joaovpg.economize.transacao.application.ConsultarTransacoes;
import com.joaovpg.economize.transacao.application.CriarTransacao;
import com.joaovpg.economize.transacao.application.PreverAlteracaoTransacao;
import com.joaovpg.economize.transacao.http.dto.request.AlterarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.ConsultaTransacoesRequest;
import com.joaovpg.economize.transacao.http.dto.request.CriarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.PreviaEdicaoTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.RepeticaoTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse;
import com.joaovpg.economize.transacao.http.dto.response.PreviaEdicaoTransacaoResponse;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
interface TransacaoHttpMapper {
  int MAXIMO_ESCOLHAS_SOBREPOSICAO = 256;

  CriarTransacao.Comando toCommand(UUID usuarioId, CriarTransacaoRequest request);

  @Mapping(target = "escopoInformado", expression = "java(request.escopoInformado())")
  @Mapping(target = "resolucaoInformada", expression = "java(request.resolucaoInformada())")
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
        request.resolucaoInformada(),
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

  /**
   * Converte a alteração com ESTA_E_AS_PROXIMAS. Política e escolhas chegam como nós JSON para que
   * a presença explícita de {@code null} seja distinguida de omissão; valores malformados são 400.
   */
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
        toRepeticao(request.repeticao()),
        converterPolitica(request.politicaSobreposicao()),
        converterEscolhas(request.resolucoesSobreposicao()),
        request.resolucaoInformada());
  }

  default ResolucaoSobreposicao converterPolitica(JsonNode politica) {
    if (politica == null || politica.isNull()) {
      return null;
    }
    try {
      if (politica.isTextual()) {
        return ResolucaoSobreposicao.valueOf(politica.asText());
      }
    } catch (IllegalArgumentException exception) {
      // Valor fora do domínio é tratado abaixo como dado inválido.
    }
    throw new ValidacaoException("politicaSobreposicao", "Politica de sobreposicao invalida");
  }

  /** Converte as escolhas, rejeitando campos desconhecidos e listas acima do limite sem truncar. */
  default List<AlterarEstaEAsProximas.Comando.Escolha> converterEscolhas(JsonNode escolhas) {
    if (escolhas == null || escolhas.isNull()) {
      return null;
    }
    if (!escolhas.isArray()) {
      throw new ValidacaoException("resolucoesSobreposicao", "Lista de escolhas invalida");
    }
    if (escolhas.size() > MAXIMO_ESCOLHAS_SOBREPOSICAO) {
      throw new ValidacaoException(
          "resolucoesSobreposicao",
          "A lista de escolhas excede o limite de " + MAXIMO_ESCOLHAS_SOBREPOSICAO + " itens");
    }
    var convertidas = new ArrayList<AlterarEstaEAsProximas.Comando.Escolha>();
    for (var escolha : escolhas) {
      convertidas.add(converterEscolha(escolha));
    }
    return convertidas;
  }

  private AlterarEstaEAsProximas.Comando.Escolha converterEscolha(JsonNode escolha) {
    var invalida = new ValidacaoException("resolucoesSobreposicao", "Escolha invalida");
    if (!escolha.isObject()) {
      throw invalida;
    }
    var data = escolha.get("dataOriginalCandidata");
    var resolucao = escolha.get("resolucao");
    var campos = new java.util.HashSet<String>();
    escolha.fieldNames().forEachRemaining(campos::add);
    if (!campos.equals(java.util.Set.of("dataOriginalCandidata", "resolucao"))
        || data == null
        || !data.isTextual()
        || resolucao == null
        || !resolucao.isTextual()) {
      throw invalida;
    }
    try {
      return new AlterarEstaEAsProximas.Comando.Escolha(
          LocalDate.parse(data.asText()), resolucao.asText());
    } catch (DateTimeException exception) {
      throw invalida;
    }
  }

  default String escopoTexto(JsonNode escopo) {
    if (escopo == null) {
      return null;
    }
    return escopo.isTextual() ? escopo.asText() : escopo.toString();
  }

  /** Converte a prévia; a política e o período são validados pelo caso de uso. */
  default PreverAlteracaoTransacao.Comando toCommandPrevia(
      UUID usuarioId, PreviaEdicaoTransacaoRequest request) {
    var alteracao = request.alteracao();
    return new PreverAlteracaoTransacao.Comando(
        usuarioId,
        alteracao.versaoEdicao(),
        escopoTexto(alteracao.escopo()),
        alteracao.escopoInformado(),
        alteracao.contaId(),
        alteracao.categoriaId(),
        alteracao.situacao(),
        alteracao.tipo(),
        alteracao.descricao(),
        alteracao.observacoes(),
        alteracao.valor(),
        alteracao.dataFinanceira(),
        toRepeticao(alteracao.repeticao()),
        request.periodo().inicio(),
        request.periodo().fim(),
        request.politicaSobreposicao());
  }

  default PreviaEdicaoTransacaoResponse toResponse(PreverAlteracaoTransacao.Resultado resultado) {
    return new PreviaEdicaoTransacaoResponse(
        resultado.versaoEdicao(),
        new PreviaEdicaoTransacaoResponse.PeriodoResponse(resultado.inicio(), resultado.fim()),
        resultado.politicaSobreposicao(),
        resultado.itensPreservados().stream().map(item -> toResponse(item)).toList(),
        resultado.ocorrenciasNovas().stream()
            .map(
                candidata ->
                    new PreviaEdicaoTransacaoResponse.CandidataResponse(
                        candidata.dataOriginal(),
                        candidata.dataFinanceira(),
                        candidata.valor(),
                        candidata.numeroParcela()))
            .toList(),
        resultado.sobreposicoes().stream()
            .map(
                sobreposicao ->
                    new PreviaEdicaoTransacaoResponse.SobreposicaoResponse(
                        sobreposicao.dataOriginalCandidata(),
                        sobreposicao.dataFinanceira(),
                        sobreposicao.chavesPreservadas(),
                        sobreposicao.resolucaoSugerida(),
                        sobreposicao.resolucoesPermitidas()))
            .toList(),
        resultado.quantidadeSobreposicoesForaPeriodo());
  }

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
