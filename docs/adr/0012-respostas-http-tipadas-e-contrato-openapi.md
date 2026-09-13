# ADR 0012: Respostas HTTP tipadas e contrato OpenAPI

## Status

Aceita

## Contexto

Os resources retornavam `jakarta.ws.rs.core.Response` em todas as operações, ocultando os DTOs do SmallRye OpenAPI e gerando schemas vazios. A API precisa expor corpos, status, parâmetros, autenticação por cookie e respostas de erro no contrato gerado.

## Decisão

A API retornará DTOs diretamente quando o sucesso for sempre `200` e usará `org.jboss.resteasy.reactive.RestResponse<T>` quando precisar preservar status, headers ou cookies (`201`, `204` e autenticação). Os mappers de erro continuarão usando `Response` para construir os problemas RFC 9457.

O parsing manual existente de parâmetros será preservado para manter o contrato de `DADOS_INVALIDOS`. Metadados OpenAPI serão adicionados somente onde os tipos Java não expressarem formato, repetição, obrigatoriedade, cookies ou headers. O filtro OpenAPI ficará restrito a preocupações transversais: respostas de erro comuns, schema de `YearMonth`, autenticação por cookie e header CSRF em operações protegidas mutáveis. Tags e nomes continuarão automáticos, com descrições explícitas apenas para operações ambíguas.

Quando uma operação declara respostas de erro adicionais e o scanner deixa de inferir o sucesso padrão, `@APIResponseSchema` explicitará apenas o schema do sucesso, sem duplicar o DTO no adapter HTTP.

## Consequências

A decisão torna o adapter HTTP dependente do `RestResponse` específico do Quarkus, elimina schemas vazios, descreve o cookie `economize_token`, o header `X-CSRF-Token` e preserva o comportamento de status, cookies e headers.

Não será criado um teste dedicado para o documento OpenAPI, conforme a decisão Q7-C. A validação ficará a cargo da compilação, da suíte existente, da inspeção do artefato gerado em `target/generated-openapi` e da revisão do contrato das rotas.
