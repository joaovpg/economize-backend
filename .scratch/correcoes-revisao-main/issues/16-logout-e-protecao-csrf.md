# 16: Logout e proteção CSRF

**What to build:** hoje o logout fica fora da proteção CSRF, e um site de terceiros pode encerrar a sessão do Usuário (logout CSRF). É preciso decidir se o logout passa a exigir o token CSRF ou se a exceção é aceita e documentada na ADR 0011.

**Blocked by:** None (can start immediately)

**Status:** needs-triage

- [ ] A decisão está registrada na ADR 0011.
- [ ] Se protegido: logout sem token CSRF válido responde `403` em RFC 9457, e o frontend continua conseguindo sair com o token devolvido pelo login.
- [ ] Testes HTTP cobrem logout com e sem token.
- [ ] `mvnw verify -B` passa.
