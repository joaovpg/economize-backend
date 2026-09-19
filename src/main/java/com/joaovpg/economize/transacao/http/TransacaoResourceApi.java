package com.joaovpg.economize.transacao.http;

import com.joaovpg.economize.transacao.http.dto.request.AlterarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.CriarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse;
import com.joaovpg.economize.transacao.http.dto.response.TransacaoResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.Explode;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterStyle;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/transacoes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface TransacaoResourceApi {
  @GET
  @RolesAllowed("usuario")
  @Tag(name = "Transações")
  @Operation(
      operationId = "consultarTransacoes",
      summary = "Consulta o extrato consolidado",
      description =
          "Consulta um período inclusivo de no máximo 12 meses; `inicio` e `fim` devem ser "
              + "informados juntos no formato `AAAA-MM`. `saldoAbertura` é o saldo antes do "
              + "primeiro dia do período, considerando saldos iniciais e impactos anteriores. "
              + "`itens` combina saldo inicial de conta, transação simples, transferência, "
              + "ocorrência recorrente e parcela. O campo `valor` é assinado: receitas e entradas "
              + "são positivas; despesas e saídas, negativas. Ao filtrar por categoria, "
              + "transferências não são incluídas. Campos de recorrência só são preenchidos para "
              + "itens recorrentes ou parcelas; `operacaoId` pode ser nulo quando o item ainda é "
              + "virtual.")
  @APIResponse(
      responseCode = "200",
      description = "Extrato consolidado consultado com sucesso.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = ConsultaTransacoesResponse.class),
              examples =
                  @ExampleObject(
                      name = "extratoComItensDeOrigemDiferente",
                      value =
                          "{\"inicio\":\"2026-02\",\"fim\":\"2026-02\",\"saldoAbertura\":1000.0000,"
                              + "\"itens\":["
                              + "{\"origem\":\"SALDO_INICIAL_CONTA\",\"operacaoId\":\"00000000-0000-0000-0000-000000000001\",\"situacao\":null,\"descricao\":\"Saldo inicial\",\"observacoes\":null,\"valor\":1000.0000,\"dataFinanceira\":\"2026-02-01\",\"efetivadoEm\":null,\"contaId\":\"00000000-0000-0000-0000-000000000001\",\"categoriaId\":null,\"contaContraparteId\":null,\"grupoRecorrenciaId\":null,\"segmentoRecorrenciaId\":null,\"dataOriginalRecorrencia\":null,\"numeroParcela\":null,\"rrule\":null,\"inicioRecorrencia\":null,\"politicaDataOcorrencia\":null},"
                              + "{\"origem\":\"TRANSACAO_SIMPLES\",\"operacaoId\":\"00000000-0000-0000-0000-000000000010\",\"situacao\":\"EFETIVADA\",\"descricao\":\"Mercado\",\"observacoes\":null,\"valor\":-250.75,\"dataFinanceira\":\"2026-02-10\",\"efetivadoEm\":\"2026-02-10T18:30:00Z\",\"contaId\":\"00000000-0000-0000-0000-000000000001\",\"categoriaId\":\"00000000-0000-0000-0000-000000000002\",\"contaContraparteId\":null,\"grupoRecorrenciaId\":null,\"segmentoRecorrenciaId\":null,\"dataOriginalRecorrencia\":null,\"numeroParcela\":null,\"rrule\":null,\"inicioRecorrencia\":null,\"politicaDataOcorrencia\":null}]}")))
  @APIResponse(
      responseCode = "404",
      description = "Conta ou categoria informada não encontrada para o usuário autenticado.")
  ConsultaTransacoesResponse consultar(
      @QueryParam("inicio")
          @Parameter(
              name = "inicio",
              in = ParameterIn.QUERY,
              description = "Mês inicial inclusivo no formato AAAA-MM.",
              required = true,
              schema =
                  @Schema(
                      type = SchemaType.STRING,
                      pattern = "\\d{4}-(0[1-9]|1[0-2])",
                      example = "\"2026-01\""))
          String inicio,
      @QueryParam("fim")
          @Parameter(
              name = "fim",
              in = ParameterIn.QUERY,
              description = "Mês final inclusivo no formato AAAA-MM; o período máximo é 12 meses.",
              required = true,
              schema =
                  @Schema(
                      type = SchemaType.STRING,
                      pattern = "\\d{4}-(0[1-9]|1[0-2])",
                      example = "\"2026-03\""))
          String fim,
      @QueryParam("contaId")
          @Parameter(
              name = "contaId",
              in = ParameterIn.QUERY,
              description = "Identificador de conta; o parâmetro pode ser repetido.",
              style = ParameterStyle.FORM,
              explode = Explode.TRUE,
              schema = @Schema(type = SchemaType.ARRAY, implementation = UUID.class),
              examples = @ExampleObject(name = "duasContas", value = "contaId=uuid-1&contaId=uuid-2"))
          List<String> contaIds,
      @QueryParam("categoriaId")
          @Parameter(
              name = "categoriaId",
              in = ParameterIn.QUERY,
              description =
                  "Identificador de categoria; o parâmetro pode ser repetido. Quando usado, "
                      + "transferências ficam fora do resultado.",
              style = ParameterStyle.FORM,
              explode = Explode.TRUE,
              schema = @Schema(type = SchemaType.ARRAY, implementation = UUID.class))
          List<String> categoriaIds);

  @PUT
  @Path("/{id}")
  @RolesAllowed("usuario")
  @Tag(name = "Transações")
  @Operation(
      operationId = "alterarTransacao",
      summary = "Altera uma transação simples",
      description =
          "Atualiza uma transação simples do usuário autenticado. Transações vinculadas a "
              + "transferências, recorrências ou parcelamentos devem ser alteradas pelo recurso "
              + "de origem. Ao mudar para `EFETIVADA`, a data financeira não pode estar no futuro; "
              + "ao voltar para `PLANEJADA`, `efetivadoEm` é limpo.")
  @APIResponseSchema(
      value = TransacaoResponse.class,
      responseCode = "200",
      responseDescription = "Transação atualizada com sucesso.")
  @APIResponse(responseCode = "404", description = "Transação ou recurso relacionado não encontrado.")
  @APIResponse(
      responseCode = "422",
      description =
          "Regra violada; exemplos: TRANSACAO_NAO_SIMPLES, DATA_FINANCEIRA_FUTURA ou "
              + "MOEDA_CONTA_INCOMPATIVEL.")
  TransacaoResponse alterar(
      @PathParam("id")
          @Parameter(
              name = "id",
              in = ParameterIn.PATH,
              description = "Identificador da transação simples.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID id,
      @Valid
          @RequestBody(
              description = "Novo estado da transação simples.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = AlterarTransacaoRequest.class)))
          AlterarTransacaoRequest request);

  @POST
  @RolesAllowed("usuario")
  @Tag(name = "Transações")
  @Operation(
      operationId = "criarTransacao",
      summary = "Cria uma transação simples",
      description =
          "Cria uma receita ou despesa para uma conta ativa do usuário autenticado. A categoria "
              + "é opcional; a conta e a categoria, quando informada, devem pertencer ao usuário. "
              + "Uma transação `EFETIVADA` não pode ter data financeira futura.")
  @APIResponseSchema(
      value = TransacaoResponse.class,
      responseCode = "201",
      responseDescription = "Transação criada com sucesso.")
  @APIResponse(responseCode = "404", description = "Conta ou categoria informada não encontrada.")
  @APIResponse(responseCode = "422", description = "Regra de negócio violada.")
  RestResponse<TransacaoResponse> criar(
      @Valid
          @RequestBody(
              description = "Dados da transação simples.",
              required = true,
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = CriarTransacaoRequest.class),
                      examples =
                          @ExampleObject(
                              name = "despesaEfetivada",
                              value =
                                  "{\"contaId\":\"00000000-0000-0000-0000-000000000001\","
                                      + "\"categoriaId\":\"00000000-0000-0000-0000-000000000002\","
                                      + "\"situacao\":\"EFETIVADA\",\"tipo\":\"DESPESA\","
                                      + "\"descricao\":\"Mercado\",\"valor\":250.75,"
                                      + "\"dataFinanceira\":\"2026-02-10\"}")))
          CriarTransacaoRequest request);

  @DELETE
  @Path("/{id}")
  @RolesAllowed("usuario")
  @Tag(name = "Transações")
  @Operation(
      operationId = "excluirTransacao",
      summary = "Exclui uma transação simples",
      description =
          "Exclui somente uma transação simples pertencente ao usuário autenticado. "
              + "Transações de transferências, recorrências e parcelamentos não devem ser removidas "
              + "por esta rota.")
  @APIResponse(responseCode = "204", description = "Transação excluída com sucesso.")
  @APIResponse(responseCode = "404", description = "Transação não encontrada.")
  @APIResponse(responseCode = "422", description = "Transação não é simples ou outra regra foi violada.")
  RestResponse<Void> excluir(
      @PathParam("id")
          @Parameter(
              name = "id",
              in = ParameterIn.PATH,
              description = "Identificador da transação simples.",
              required = true,
              schema = @Schema(implementation = UUID.class))
          UUID id);
}
