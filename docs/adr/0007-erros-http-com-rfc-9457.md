# ADR 0007: Erros HTTP com RFC 9457

## Status

Aceita

## Contexto

A API publicava erros de domínio e validação em formatos próprios, com campos `codigo`, `mensagem` e `campos`. Isso dificultava o consumo uniforme por clientes e não identificava o tipo do problema conforme o padrão de erros HTTP.

## Decisão

Adotar `application/problem+json` conforme a RFC 9457 usando a extensão `quarkus-http-problem` para os mapeamentos de infraestrutura e segurança.

Os problemas específicos do Economize usam:

- `type`: URI estável no formato `urn:economize:problem:<codigo>`;
- `title`: resumo legível do tipo do problema;
- `status`: status HTTP;
- `detail`: explicação da ocorrência;
- `instance`: URI da requisição que originou o problema;
- `errors`: lista opcional de objetos `{field, detail}` para erros associados a campos.

Os mapeadores de exceções de domínio permanecem próprios da aplicação para preservar os códigos e status do contrato. Problemas de validação semântica usam `400`; regras de negócio usam `422`; recursos inexistentes usam `404`; autenticação inválida usa `401`.

Os nomes legados `codigo`, `mensagem` e `campos` não são mantidos como aliases.

Versão desatualizada (`urn:economize:problem:VERSAO_DESATUALIZADA`) responde `409 Conflict`, não `422`, e não contém dados financeiros. Perdas de corrida de versão na persistência (`OptimisticLockException`) recebem o mesmo problema.

## Consequências

Clientes devem extrair códigos do sufixo de `type` e tratar `errors` como uma lista. A extensão fornece respostas padronizadas para exceções de infraestrutura, parsing JSON, segurança e problemas inesperados. O contrato específico de validação continua controlado pelo mapeador da aplicação.
