# 06: Replanejar ocorrência ou Parcela efetivada

**What to build:** "Parcelas efetivadas admitem correção ou exclusão física conforme o ciclo de vida das Transações simples." O Usuário passa a informar a Situação da transação ao editar uma ocorrência persistida, podendo voltar uma ocorrência ou Parcela de `EFETIVADA` para `PLANEJADA` e efetivá-la de novo depois.

**Blocked by:** 04 — Ocorrência efetivada preservada após `THIS_AND_FUTURE` continua editável e excluível

**Status:** ready-for-agent

- [ ] A edição `ONLY_THIS` de uma ocorrência aceita `situacao`, documentada no OpenAPI.
- [ ] `EFETIVADA` → `PLANEJADA` limpa `efetivadoEm`; uma nova efetivação registra outro instante.
- [ ] Manter `EFETIVADA` preserva o `efetivadoEm` original.
- [ ] A Parcela replanejada mantém `numeroParcela` e `totalParcelas`.
- [ ] Efetivar com Data financeira futura no fuso do Usuário continua rejeitado.
- [ ] Testes HTTP cobrem as transições para Recorrência comum e Parcelamento.
- [ ] `mvnw verify -B` passa.
