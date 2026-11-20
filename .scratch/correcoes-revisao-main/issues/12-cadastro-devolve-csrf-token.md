# 12: Cadastro devolve `csrfToken`

**What to build:** o cadastro já autentica o Usuário por cookie. Assim como o login, passa a devolver o `csrfToken`, e o frontend consegue fazer escritas protegidas logo após o cadastro, sem chamar o login (ADR 0011).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] A resposta de cadastro inclui `csrfToken`, com o mesmo contrato do login, documentado no OpenAPI.
- [ ] Uma escrita protegida feita logo após o cadastro, com esse token, é aceita.
- [ ] O cadastro continua sem expor dados sensíveis.
- [ ] Testes HTTP cobrem o fluxo cadastro → escrita protegida.
- [ ] `mvnw verify -B` passa.
