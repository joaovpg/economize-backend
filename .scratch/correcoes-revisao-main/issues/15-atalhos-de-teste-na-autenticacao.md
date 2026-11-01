# 15: Atalhos de teste na autenticação de produção

**What to build:** o código de produção mantém dois atalhos ativados por uma flag de compatibilidade de testes: um header que pula a proteção CSRF e a aceitação de `Authorization: Bearer`. A ADR 0011 afirma que "o header `Authorization` não é aceito". É preciso decidir entre remover os atalhos (migrando os testes para cookie + CSRF reais) e registrar a exceção na ADR com a garantia de que a flag nunca é ativada fora do profile `test`.

**Blocked by:** None (can start immediately)

**Status:** needs-triage

- [ ] A decisão está registrada (ADR 0011 atualizada ou nova ADR).
- [ ] Se removidos: os testes autenticam por cookie e enviam o token CSRF, e nenhum caminho de produção aceita `Authorization` nem o header de teste.
- [ ] Se mantidos: um teste garante que, fora do profile `test`, os dois atalhos são rejeitados.
- [ ] `mvnw verify -B` passa.
