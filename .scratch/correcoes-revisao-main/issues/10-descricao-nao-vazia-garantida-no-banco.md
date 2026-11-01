# 10: Descrição não vazia garantida também no banco

**What to build:** a invariante "descrição não pode ser vazia nem só espaços", hoje aplicada pela aplicação a Transações, Transferências e Recorrências, passa a ser garantida também pelo PostgreSQL, como já acontece com uma das tabelas. A mudança vem por migration incremental, com constraints nomeadas conforme as convenções físicas de `docs/modelo-financeiro.md`, sem alterar `V1`.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Uma nova migration incremental adiciona as constraints de descrição não vazia às tabelas que ainda não as têm.
- [ ] A migration trata dados pré-existentes inválidos de forma determinística e documentada.
- [ ] Um teste de migration migra até a versão anterior, insere descrições inválidas e aplica as migrations restantes.
- [ ] O mapeamento JPA continua validando com `validate`.
- [ ] `docs/modelo-financeiro.md` lista as novas constraints.
- [ ] `mvnw verify -B` passa.
