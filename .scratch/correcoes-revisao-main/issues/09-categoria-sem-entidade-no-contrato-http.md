# 09: Categoria sem entidade no contrato HTTP

**What to build:** os casos de uso de Categoria (cadastrar, editar, listar) passam a devolver um resultado próprio da aplicação, como Conta financeira já faz, e o adapter HTTP de Categoria deixa de depender da entidade JPA. Cumpre a regra do AGENTS.md: "Não exponha entidades no contrato HTTP".

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Nenhuma classe HTTP do módulo de Categoria importa a entidade ou o repository.
- [ ] O mapper continua sendo um bean MapStruct com `toCommand`/`toResponse` e sem regras.
- [ ] JSON de resposta e OpenAPI gerado permanecem idênticos.
- [ ] Os testes HTTP de Categoria passam sem alteração; `mvnw verify -B` passa.
