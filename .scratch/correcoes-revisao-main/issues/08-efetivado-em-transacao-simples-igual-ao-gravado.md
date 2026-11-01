# 08: `efetivadoEm` das Transações simples igual ao valor gravado

**What to build:** criar uma Transação simples já `EFETIVADA`, ou efetivar uma `PLANEJADA`, devolve na resposta o mesmo `efetivadoEm` que fica persistido no PostgreSQL, na precisão de microssegundos. É o comportamento que Transferências e Recorrências já têm.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] O `efetivadoEm` da resposta de criação e de alteração é idêntico ao devolvido por uma consulta posterior.
- [ ] Testes HTTP comparam a resposta da escrita com uma releitura, na criação e na transição para `EFETIVADA`.
- [ ] `mvnw verify -B` passa.
