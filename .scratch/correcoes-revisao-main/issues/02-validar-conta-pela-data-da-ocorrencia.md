# 02: Validar a Conta financeira pela Data financeira da ocorrência editada

**What to build:** ao editar uma ocorrência recorrente com escopo `ONLY_THIS` ou `THIS_AND_FUTURE` e trocar a Conta financeira, a compatibilidade com a data do saldo inicial da nova conta passa a ser verificada pela Data financeira da ocorrência editada (ou pela nova data de início, no corte), e não pelo início do Segmento original. Hoje o Usuário recebe `DATA_INICIAL_ANTERIOR_SALDO_INICIAL` indevidamente quando a nova conta tem saldo inicial posterior ao início da série, mas anterior à ocorrência.

**Blocked by:** 01 — Separar o gerenciamento de ocorrências recorrentes em casos de uso por intenção

**Status:** ready-for-agent

- [ ] Editar `ONLY_THIS` para uma conta cujo saldo inicial é posterior ao início da série e anterior ou igual à Data financeira da ocorrência é aceito.
- [ ] Editar `THIS_AND_FUTURE` valida a conta pela data de início do novo Segmento.
- [ ] Uma conta com saldo inicial posterior à data validada continua sendo rejeitada com `422`.
- [ ] Testes HTTP cobrem os dois escopos, nos casos aceito e rejeitado.
- [ ] `mvnw verify -B` passa.
