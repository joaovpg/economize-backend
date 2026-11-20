# Instruções para agentes

## Fontes de verdade

- Antes de nomear ou alterar conceitos financeiros, leia `CONTEXT.md`, `docs/modelo-financeiro.md` e as ADRs relacionadas em `docs/adr/`. Use `Transacao`, nunca `Lancamento`, e nomeie casos de uso pela intenção (`CriarTransacao`, `CadastrarConta`), não como `Service`.
- `docs/roadmap.md` descreve também capacidades futuras. Confirme no código e no `README.md` o que já existe; Transferências e Recorrências ainda têm principalmente persistência, não fluxos públicos completos.
- Issues e especificações locais ficam em `.scratch/`; as convenções estão em `docs/agents/issue-tracker.md`. Para documentação de domínio, siga `docs/agents/domain.md`.

## Estrutura e fluxo

- Este é um único módulo Maven, com Java 25 e Quarkus 3.37.4. O código é organizado primeiro por domínio em `src/main/java/com/joaovpg/economize/{usuario,conta,categoria,transacao,transferencia,recorrencia}`; não crie pacotes globais por camada.
- Módulos começam planos e só ganham `http`, `application`, `domain` ou `persistence` quando houver responsabilidade concreta. `shared` contém apenas preocupações realmente transversais. Subpackages agrupam por papel, não por tipo de construção Java (sem `enums/`); veja a convenção na ADR 0001.
- O fluxo existente é `Resource -> mapper MapStruct -> caso de uso -> repository Panache`. Resources validam o contrato HTTP, extraem o usuário do JWT e traduzem a resposta; regras, autorização por proprietário e transações pertencem ao caso de uso.
- DTOs HTTP ficam em `<modulo>/http/dto/{request,response}` e não dependem de entidades ou repositories. Mappers ficam junto ao adapter HTTP, são beans CDI do MapStruct, usam nomes como `toCommand` e `toResponse` e não contêm regras.
- Entidades JPA são modelos passivos de persistência. Use UUID para IDs, `BigDecimal` para dinheiro, `LocalDate` para datas financeiras e `Instant` para instantes. Não exponha entidades no contrato HTTP.
- A API REST usa o prefixo `/api`, definido em `EconomizeApp`; por exemplo, Transações estão em `/api/transacoes`.
- Toda busca ou alteração de dado financeiro deve restringir pelo ID do usuário autenticado. Recursos inexistentes e pertencentes a outro usuário são deliberadamente indistinguíveis para a API.

## Banco e geração

- Flyway em `src/main/resources/db/migration` é a autoridade do schema; o Hibernate está configurado somente para `validate`. Mudanças de schema exigem migration incremental e mapeamento JPA compatível.
- `V1__criar_nucleo_financeiro.sql` é a migration inicial compartilhada e não deve ser alterada. Preserve a numeração permanente e as convenções físicas de tabelas, colunas, índices e constraints descritas em `docs/modelo-financeiro.md`.
- Invariantes financeiras críticas existem na aplicação e também em constraints/triggers PostgreSQL. Ao alterar uma delas, verifique ambos os lados e os testes de migration.
- MapStruct gera implementações durante a compilação e o OpenAPI é gravado em `target/generated-openapi`. Não edite conteúdo de `target/`.

## Desenvolvimento e verificação

- No Windows, prepare `.env` a partir de `.env.example`, execute `docker compose up -d` para o PostgreSQL local e inicie com `.\mvnw.cmd quarkus:dev`. Swagger UI fica em `http://localhost:8080/q/swagger-ui`.
- A suíte usa `@QuarkusTest`, REST Assured e PostgreSQL real iniciado pelo Quarkus Dev Services. Docker deve estar disponível, mas não é necessário subir o banco do Compose para os testes; o profile `test` usa um banco temporário isolado.
- Testes de migrations criam schemas PostgreSQL próprios, migram até uma versão antiga, preparam dados e então aplicam as migrations restantes. Preserve esse padrão para mudanças de schema.
- Execute toda a suíte com `.\mvnw.cmd test`.
- Execute uma classe com `.\mvnw.cmd -Dtest=TransacaoResourceTest test`.
- Execute um método com `.\mvnw.cmd -Dtest=TransacaoResourceTest#criaTransacaoPlanejadaParaUsuarioAutenticado test`.
- A única etapa da CI é `./mvnw verify -B` com JDK 25. Antes de concluir alterações de código, configuração ou banco, rode no Windows `.\mvnw.cmd verify -B`; não há etapas separadas de lint, formatter, typecheck ou cobertura.
- Alterações exclusivamente documentais não exigem build Maven; revise o diff e valide os comandos e caminhos mencionados.
