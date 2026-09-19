# ADR 0013: Contrato OpenAPI declarado em interfaces de Resources

## Status

Aceita

## Contexto

O contrato OpenAPI precisa deixar de depender de nomes e resumos automáticos e passar a descrever todas as operações públicas, seus retornos, regras de negócio, exemplos e códigos de erro. As anotações diretamente nas classes dos Resources misturam metadados HTTP com a implementação e tornam os métodos difíceis de ler.

## Decisão

Cada Resource público terá uma interface de contrato no mesmo módulo HTTP. A interface declarará a assinatura da operação e concentrará as anotações MicroProfile OpenAPI, incluindo `operationId`, tags, resumo, descrição, parâmetros, corpos, respostas, regras e exemplos. A classe concreta implementará essa interface e permanecerá responsável somente pela adaptação HTTP e execução do caso de uso.

Os `operationId`s e tags serão estáveis e em português, alinhados ao vocabulário do domínio. Os DTOs HTTP também receberão anotações `@Schema` para documentar campos, formatos, limites, nulabilidade, obrigatoriedade explícita (`required=true/false`) e significados condicionais. Campos condicionais serão opcionais no schema base, com a regra que os torna obrigatórios descrita no próprio campo e na operação. O formato JSON existente será preservado nesta etapa.

O filtro OpenAPI continuará reservado para preocupações transversais, como autenticação por cookie, CSRF, schemas e respostas de erro comuns. Respostas e regras específicas permanecerão no contrato da operação.

## Consequências

O Swagger passa a apresentar um contrato legível sem poluir a implementação dos Resources. A assinatura da interface e a classe concreta precisam permanecer compatíveis, e novas operações públicas devem ser adicionadas ao contrato antes da implementação. A decisão refina a ADR 0012: nomes, tags e descrições deixam de ser automáticos quando a documentação da operação exigir semântica explícita.

## Base técnica

- [Quarkus — OpenAPI e Swagger UI](https://quarkus.io/guides/openapi-swaggerui): integração com MicroProfile OpenAPI, anotações globais e opções de configuração do Swagger UI.
- [Swagger — Paths and Operations](https://swagger.io/docs/specification/v3_0/paths-and-operations/): uso de `operationId`, resumo, descrição, tags, parâmetros e respostas por operação.
- [Swagger — Adding Examples](https://swagger.io/docs/specification/v3_0/adding-examples/): exemplos de parâmetros, schemas, corpos de requisição e respostas.
