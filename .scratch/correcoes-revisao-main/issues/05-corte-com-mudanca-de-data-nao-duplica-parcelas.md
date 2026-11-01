# 05: `THIS_AND_FUTURE` com mudança de data não duplica Parcelas

**What to build:** quando uma edição `THIS_AND_FUTURE` de um Parcelamento muda a data-âncora (por exemplo, do dia 10 para o dia 15), as Parcelas já efetivadas após o corte suprimem a Parcela de mesmo número projetada pelo novo Segmento. A spec exige que esses casos "não produzam duplicatas": a numeração continua estável e o plano nunca passa da quantidade total.

**Blocked by:** 01 — Separar o gerenciamento de ocorrências recorrentes em casos de uso por intenção; 04 — Ocorrência efetivada preservada após `THIS_AND_FUTURE` continua editável e excluível

**Status:** ready-for-agent

- [ ] Num Parcelamento de 12 com a Parcela 6 efetivada, um corte na Parcela 4 que muda o dia do mês resulta em exatamente 12 Parcelas, numeradas de 1 a 12 sem repetição.
- [ ] A Parcela 6 efetivada mantém Data financeira, `numeroParcela` e `totalParcelas`.
- [ ] Recorrências comuns preservam o comportamento atual quando a data coincide.
- [ ] Testes HTTP cobrem o corte com e sem mudança de data.
- [ ] `mvnw verify -B` passa.
