package com.joaovpg.economize.recorrencia.http;

import com.joaovpg.economize.recorrencia.enums.EscopoOcorrencia;
import com.joaovpg.economize.recorrencia.http.dto.request.AlterarOcorrenciaRecorrenteRequest;
import com.joaovpg.economize.recorrencia.http.dto.request.CriarRecorrenciaRequest;
import com.joaovpg.economize.recorrencia.http.dto.request.EfetivarOcorrenciaRecorrenteRequest;
import com.joaovpg.economize.recorrencia.http.dto.response.RecorrenciaOperacaoResponse;
import com.joaovpg.economize.recorrencia.http.dto.response.RecorrenciaResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/recorrencias")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("usuario")
public interface RecorrenciaResourceApi {
  @POST
  @Tag(name = "Recorrências")
  @Operation(
      operationId = "criarRecorrencia",
      summary = "Cria uma recorrência ou parcelamento",
      description =
          "A operação é escolhida por `tipoGrupo`. Com `RECORRENCIA`, gera ocorrências pela "
              + "regra de frequência e termina por `quantidadeOcorrencias` ou `ate`; com "
              + "`PARCELAMENTO`, usa `numeroPrimeiraParcela` e `quantidadeTotalOriginal` para "
              + "calcular as parcelas restantes. `frequencia` é obrigatória nos dois casos. "
              + "Para recorrência mensal, `diasMes` deve conter ao menos um dia. A conta deve ser "
              + "ativa e a data inicial não pode ser anterior ao saldo inicial da conta. O retorno "
              + "inclui a regra RFC 5545 em `rrule`; campos de parcelamento ficam nulos para "
              + "recorrências comuns.")
  @APIResponseSchema(
      value = RecorrenciaResponse.class,
      responseCode = "201",
      responseDescription = "Recorrência ou parcelamento criado com sucesso.")
  @APIResponse(
      responseCode = "404",
      description = "Conta, categoria ou usuário relacionado não encontrado.")
  @APIResponse(
      responseCode = "422",
      description =
          "Regra violada; exemplos: RRULE_INVALIDA, FREQUENCIA_INVALIDA, "
              + "NUMERO_PRIMEIRA_PARCELA_INVALIDO ou QUANTIDADE_PARCELAS_INVALIDA.")
  RestResponse<RecorrenciaResponse> criar(
      @Valid @RequestBody(
              description =
                  "Use tipoGrupo=RECORRENCIA para uma série por regra ou "
                      + "tipoGrupo=PARCELAMENTO para parcelas numeradas.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = CriarRecorrenciaRequest.class),
                      examples = {
                        @ExampleObject(
                            name = "recorrenciaMensal",
                            summary = "Recorrência mensal sem data final",
                            value =
                                "{\"tipoGrupo\":\"RECORRENCIA\",\"contaId\":\"00000000-0000-0000-0000-000000000001\","
                                    + "\"tipo\":\"DESPESA\",\"descricao\":\"Aluguel\",\"valor\":1800.00,"
                                    + "\"inicio\":\"2026-01-05\",\"frequencia\":\"MONTHLY\","
                                    + "\"diasMes\":[5],\"intervalo\":1}"),
                        @ExampleObject(
                            name = "parcelamento",
                            summary = "Parcelamento a partir da terceira parcela",
                            value =
                                "{\"tipoGrupo\":\"PARCELAMENTO\",\"contaId\":\"00000000-0000-0000-0000-000000000001\","
                                    + "\"tipo\":\"DESPESA\",\"descricao\":\"Curso\",\"valor\":300.00,"
                                    + "\"inicio\":\"2026-01-10\",\"frequencia\":\"MONTHLY\","
                                    + "\"intervalo\":1,\"numeroPrimeiraParcela\":3,\"quantidadeTotalOriginal\":12}")
                      }))
          CriarRecorrenciaRequest request);

  @PUT
  @Path("/{segmentoId}/ocorrencias/{dataOriginal}")
  @Tag(name = "Recorrências")
  @Operation(
      operationId = "alterarOcorrenciaRecorrente",
      summary = "Altera uma ocorrência recorrente",
      description =
          "`escopo=ONLY_THIS` altera somente a ocorrência selecionada. "
              + "`escopo=THIS_AND_FUTURE` cria uma nova regra para a ocorrência e as próximas; "
              + "esse escopo só pode ser usado em ocorrência virtual. Alterar a quantidade total "
              + "de parcelas exige `THIS_AND_FUTURE` e só vale para parcelamentos. A data da nova "
              + "regra não pode ser anterior à ocorrência selecionada. O retorno representa a "
              + "transação criada/atualizada ou o segmento virtual resultante.")
  @APIResponseSchema(
      value = RecorrenciaOperacaoResponse.class,
      responseCode = "200",
      responseDescription = "Ocorrência alterada com sucesso.")
  @APIResponse(responseCode = "404", description = "Segmento ou ocorrência não encontrada.")
  @APIResponse(
      responseCode = "422",
      description =
          "Regra violada; exemplos: ESCOPO_OCORRENCIA_INVALIDO, OCORRENCIA_CANCELADA, "
              + "QUANTIDADE_PARCELAS_INVALIDA ou RRULE_INVALIDA.")
  RecorrenciaOperacaoResponse editar(
      @PathParam("segmentoId")
          @Parameter(
              name = "segmentoId",
              in = ParameterIn.PATH,
              description = "Identificador do segmento da recorrência ou parcelamento.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID segmentoId,
      @PathParam("dataOriginal")
          @Parameter(
              name = "dataOriginal",
              in = ParameterIn.PATH,
              description = "Data original da ocorrência no formato AAAA-MM-DD.",
              required = true,
              schema = @Schema(implementation = LocalDate.class, format = "date"))
          LocalDate dataOriginal,
      @Valid @RequestBody(
              description = "Novo estado e escopo da alteração.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = AlterarOcorrenciaRecorrenteRequest.class),
                      examples =
                          @ExampleObject(
                              name = "alterarEstaEAsProximas",
                              value =
                                  "{\"escopo\":\"THIS_AND_FUTURE\",\"contaId\":\"00000000-0000-0000-0000-000000000001\",\"tipo\":\"DESPESA\",\"descricao\":\"Aluguel"
                                      + " reajustado\","
                                      + "\"valor\":1900.00,\"dataFinanceira\":\"2026-04-05\","
                                      + "\"frequencia\":\"MONTHLY\",\"diasMes\":[5],\"semTermino\":true}")))
          AlterarOcorrenciaRecorrenteRequest request);

  @POST
  @Path("/{segmentoId}/ocorrencias/{dataOriginal}/efetivar")
  @Tag(name = "Recorrências")
  @Operation(
      operationId = "efetivarOcorrenciaRecorrente",
      summary = "Efetiva uma ocorrência recorrente",
      description =
          "Materializa a ocorrência virtual como uma transação efetivada. O corpo é opcional: "
              + "sem `dataFinanceira`, usa a data original da ocorrência; com o campo informado, "
              + "usa a nova data financeira. A data efetivada não pode estar no futuro.")
  @APIResponseSchema(
      value = RecorrenciaOperacaoResponse.class,
      responseCode = "200",
      responseDescription = "Ocorrência efetivada com sucesso.")
  @APIResponse(responseCode = "404", description = "Segmento ou ocorrência não encontrada.")
  @APIResponse(
      responseCode = "422",
      description = "Ocorrência já efetivada, cancelada ou com data financeira inválida.")
  RecorrenciaOperacaoResponse efetivar(
      @PathParam("segmentoId")
          @Parameter(
              name = "segmentoId",
              in = ParameterIn.PATH,
              description = "Identificador do segmento da recorrência ou parcelamento.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID segmentoId,
      @PathParam("dataOriginal")
          @Parameter(
              name = "dataOriginal",
              in = ParameterIn.PATH,
              description = "Data original da ocorrência no formato AAAA-MM-DD.",
              required = true,
              schema = @Schema(implementation = LocalDate.class, format = "date"))
          LocalDate dataOriginal,
      @RequestBody(
              description = "Opcional; informe dataFinanceira para efetivar em outra data.",
              required = false,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = EfetivarOcorrenciaRecorrenteRequest.class),
                      examples =
                          @ExampleObject(
                              name = "efetivarEmOutraData",
                              value = "{\"dataFinanceira\":\"2026-04-06\"}")))
          EfetivarOcorrenciaRecorrenteRequest request);

  @DELETE
  @Path("/{segmentoId}/ocorrencias/{dataOriginal}")
  @Tag(name = "Recorrências")
  @Operation(
      operationId = "excluirOcorrenciaRecorrente",
      summary = "Exclui uma ocorrência recorrente",
      description =
          "Por padrão, `escopo=ONLY_THIS` suprime somente a ocorrência selecionada. "
              + "`THIS_AND_FUTURE` também pode ser usado somente quando a ocorrência é virtual; "
              + "nesse caso, interrompe a geração a partir dela.")
  @APIResponse(responseCode = "204", description = "Ocorrência excluída com sucesso.")
  @APIResponse(responseCode = "404", description = "Segmento ou ocorrência não encontrada.")
  @APIResponse(
      responseCode = "422",
      description = "Escopo inválido ou ocorrência não pode ser excluída.")
  RestResponse<Void> excluir(
      @PathParam("segmentoId")
          @Parameter(
              name = "segmentoId",
              in = ParameterIn.PATH,
              description = "Identificador do segmento da recorrência ou parcelamento.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID segmentoId,
      @PathParam("dataOriginal")
          @Parameter(
              name = "dataOriginal",
              in = ParameterIn.PATH,
              description = "Data original da ocorrência no formato AAAA-MM-DD.",
              required = true,
              schema = @Schema(implementation = LocalDate.class, format = "date"))
          LocalDate dataOriginal,
      @QueryParam("escopo")
          @DefaultValue("ONLY_THIS")
          @Parameter(
              name = "escopo",
              in = ParameterIn.QUERY,
              description = "Escopo da exclusão; o padrão é somente a ocorrência selecionada.",
              schema = @Schema(implementation = EscopoOcorrencia.class),
              required = false,
              example = "ONLY_THIS")
          EscopoOcorrencia escopo);
}
