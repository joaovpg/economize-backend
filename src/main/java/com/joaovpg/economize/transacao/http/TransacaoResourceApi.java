package com.joaovpg.economize.transacao.http;

import com.joaovpg.economize.transacao.http.dto.request.AlterarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.CriarTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.request.PreviaEdicaoTransacaoRequest;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse;
import com.joaovpg.economize.transacao.http.dto.response.ConsultaTransacoesResponse.ItemResponse;
import com.joaovpg.economize.transacao.http.dto.response.PreviaEdicaoTransacaoResponse;
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
                              + "\"itens\":[{\"origem\":\"SALDO_INICIAL_CONTA\",\"operacaoId\":\"00000000-0000-0000-0000-000000000001\",\"situacao\":null,\"descricao\":\"Saldo"
                              + " inicial\",\"observacoes\":null,\"valor\":1000.0000,\"dataFinanceira\":\"2026-02-01\",\"efetivadoEm\":null,\"contaId\":\"00000000-0000-0000-0000-000000000001\",\"categoriaId\":null,\"contaContraparteId\":null,\"chaveEdicao\":null,\"versaoEdicao\":null,\"tipo\":null,\"virtual\":null,\"protegida\":null,\"serie\":null,\"repeticao\":null,\"escoposDisponiveis\":[]},"
                              + "{\"origem\":\"TRANSACAO_SIMPLES\",\"operacaoId\":\"00000000-0000-0000-0000-000000000010\",\"situacao\":\"EFETIVADA\",\"descricao\":\"Mercado\",\"observacoes\":null,\"valor\":-250.75,\"dataFinanceira\":\"2026-02-10\",\"efetivadoEm\":\"2026-02-10T18:30:00Z\",\"contaId\":\"00000000-0000-0000-0000-000000000001\",\"categoriaId\":\"00000000-0000-0000-0000-000000000002\",\"contaContraparteId\":null,\"chaveEdicao\":\"chave-opaca\",\"versaoEdicao\":\"versao-opaca\",\"tipo\":\"DESPESA\",\"virtual\":false,\"protegida\":false,\"serie\":null,\"repeticao\":null,\"escoposDisponiveis\":[]}]}")))
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
                      examples = {"\"2026-01\""}))
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
                      examples = {"\"2026-03\""}))
          String fim,
      @QueryParam("contaId")
          @Parameter(
              name = "contaId",
              in = ParameterIn.QUERY,
              description = "Identificador de conta; o parâmetro pode ser repetido.",
              style = ParameterStyle.FORM,
              explode = Explode.TRUE,
              schema = @Schema(type = SchemaType.ARRAY, implementation = UUID.class),
              examples =
                  @ExampleObject(name = "duasContas", value = "contaId=uuid-1&contaId=uuid-2"))
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
  @Path("/{chaveEdicao}")
  @RolesAllowed("usuario")
  @Tag(name = "Transações")
  @Operation(
      operationId = "alterarTransacao",
      summary = "Altera uma transação simples ou a converte em série",
      description =
          "Atualiza uma transação simples do usuário autenticado. Transações vinculadas a "
              + "transferências ou já pertencentes a uma série devem ser alteradas pelo recurso "
              + "de origem. Com `repeticao` informada, a transação simples torna-se a primeira "
              + "ocorrência de uma repetição ou parcelamento, sem cópia, e a resposta traz a nova "
              + "chave. Ao mudar para `EFETIVADA`, a data financeira não pode estar no futuro; "
              + "ao voltar para `PLANEJADA`, `efetivadoEm` é limpo. Informe todos os campos, "
              + "inclusive os anuláveis e `repeticao` (`null` mantém a transação simples), e a "
              + "`versaoEdicao` vigente.")
  @APIResponseSchema(
      value = ItemResponse.class,
      responseCode = "200",
      responseDescription = "Transação atualizada com sucesso.")
  @APIResponse(
      responseCode = "404",
      description = "Transação ou recurso relacionado não encontrado.")
  @APIResponse(
      responseCode = "409",
      description = "VERSAO_DESATUALIZADA: a transação foi alterada por outra operação.")
  @APIResponse(
      responseCode = "422",
      description =
          "Regra violada; exemplos: ESCOPO_INVALIDO, DATA_FINANCEIRA_FUTURA ou "
              + "MOEDA_CONTA_INCOMPATIVEL.")
  ItemResponse alterar(
      @PathParam("chaveEdicao")
          @Parameter(
              name = "chaveEdicao",
              in = ParameterIn.PATH,
              description = "Chave opaca da transação simples.",
              required = true,
              schema = @Schema(type = SchemaType.STRING))
          String chaveEdicao,
      @Valid @RequestBody(
              description = "Novo estado da transação simples.",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = AlterarTransacaoRequest.class)))
          AlterarTransacaoRequest request);

  @POST
  @Path("/{chaveEdicao}/previa-edicao")
  @RolesAllowed("usuario")
  @Tag(name = "Transações")
  @Operation(
      operationId = "previarEdicaoTransacao",
      summary = "Calcula a prévia de uma alteração e suas sobreposições",
      description =
          "Calcula, sem gravar nada, as ocorrências que a alteração produziria no período e as"
              + " colisões com ocorrências protegidas posteriores ao corte. Uma colisão existe"
              + " quando a data financeira de uma nova ocorrência coincide com a de uma ocorrência"
              + " protegida. A prévia não gera token: o PUT recalcula e revalida. Para transação"
              + " simples, omita `escopo`; para ocorrência de série, informe `ESTA_E_AS_PROXIMAS`.")
  @APIResponseSchema(
      value = PreviaEdicaoTransacaoResponse.class,
      responseCode = "200",
      responseDescription = "Prévia calculada sem gravação.")
  @APIResponse(
      responseCode = "400",
      description =
          "DADOS_INVALIDOS: campo ausente ou desconhecido, período fora de 1 a 12 meses ou"
              + " política não aceita nesta operação.")
  @APIResponse(
      responseCode = "404",
      description = "Transação, série ou ocorrência não encontrada para o usuário autenticado.")
  @APIResponse(
      responseCode = "409",
      description = "VERSAO_DESATUALIZADA: a versão informada não é a vigente.")
  @APIResponse(
      responseCode = "422",
      description = "Regra violada; exemplos: ESCOPO_INVALIDO ou REPETICAO_INVALIDA.")
  PreviaEdicaoTransacaoResponse previaEdicao(
      @PathParam("chaveEdicao")
          @Parameter(
              name = "chaveEdicao",
              in = ParameterIn.PATH,
              description = "Chave opaca da transação simples ou da ocorrência de série.",
              required = true,
              schema = @Schema(type = SchemaType.STRING))
          String chaveEdicao,
      @Valid @RequestBody(
              description = "Alteração proposta, período e política geral de sobreposição.",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      schema = @Schema(implementation = PreviaEdicaoTransacaoRequest.class)))
          PreviaEdicaoTransacaoRequest request);

  @POST
  @RolesAllowed("usuario")
  @Tag(name = "Transações")
  @Operation(
      operationId = "criarTransacao",
      summary = "Cria uma transação simples, repetida ou parcelada",
      description =
          "Cria uma receita ou despesa para uma conta ativa do usuário autenticado. A categoria "
              + "é opcional; a conta e a categoria, quando informada, devem pertencer ao usuário. "
              + "Uma transação `EFETIVADA` não pode ter data financeira futura. Com `repeticao` "
              + "nulo, cria uma transação simples. Com repetição ou parcelamento, cria a série "
              + "e responde com a primeira ocorrência no mesmo formato da leitura: efetivada é "
              + "materializada uma vez; planejada permanece virtual e é identificada pela chave "
              + "do segmento e pela data original. `politicaData` é sempre explícita e devolvida.")
  @APIResponseSchema(
      value = ItemResponse.class,
      responseCode = "201",
      responseDescription = "Transação ou primeira ocorrência criada, com Location.")
  @APIResponse(
      responseCode = "400",
      description = "DADOS_INVALIDOS: campo ausente ou desconhecido.")
  @APIResponse(responseCode = "404", description = "Conta ou categoria informada não encontrada.")
  @APIResponse(
      responseCode = "422",
      description =
          "Regra violada; exemplos: REPETICAO_INVALIDA (frequência, dias, política, início ou "
              + "término incompatíveis), PARCELAMENTO_INVALIDO (numeração ou término com "
              + "parcelamento) ou DATA_FINANCEIRA_FUTURA.")
  RestResponse<ItemResponse> criar(
      @Valid @RequestBody(
              description = "Dados da transação simples.",
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
                                      + "\"descricao\":\"Mercado\",\"observacoes\":null,\"valor\":250.75,"
                                      + "\"dataFinanceira\":\"2026-02-10\",\"repeticao\":null}")))
          CriarTransacaoRequest request);

  @DELETE
  @Path("/{id}")
  @RolesAllowed("usuario")
  @Tag(name = "Transações")
  @Operation(
      operationId = "excluirTransacao",
      summary = "Exclui uma transação simples",
      description =
          "Exclui somente uma transação simples pertencente ao usuário autenticado. Transações de"
              + " transferências, recorrências e parcelamentos não devem ser removidas por esta"
              + " rota.")
  @APIResponse(responseCode = "204", description = "Transação excluída com sucesso.")
  @APIResponse(responseCode = "404", description = "Transação não encontrada.")
  @APIResponse(
      responseCode = "422",
      description = "Transação não é simples ou outra regra foi violada.")
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
