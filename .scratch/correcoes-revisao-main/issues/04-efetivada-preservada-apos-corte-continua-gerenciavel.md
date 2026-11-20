# 04: Ocorrência efetivada preservada após `THIS_AND_FUTURE` continua editável e excluível

**What to build:** as ocorrências efetivadas a partir do corte de uma edição `THIS_AND_FUTURE` são "preservadas integralmente como exceções". Hoje, como o Segmento antigo é encerrado antes da data delas, os endpoints de ocorrência respondem `404` e o fluxo de Transações simples responde `422 TRANSACAO_NAO_SIMPLES`. Na prática, o Usuário só consegue excluí-las. O Usuário deve conseguir corrigir e excluir essas exceções pelo endpoint de ocorrências, como faz com qualquer ocorrência persistida.

**Blocked by:** 01 — Separar o gerenciamento de ocorrências recorrentes em casos de uso por intenção

**Status:** ready-for-agent

- [ ] Uma efetivada posterior ao corte pode ser editada com escopo `ONLY_THIS` pelo endpoint de ocorrências.
- [ ] Ela pode ser excluída pelo endpoint de ocorrências.
- [ ] `THIS_AND_FUTURE` continua aceito apenas para ocorrência virtual, conforme o glossário.
- [ ] Datas que nunca foram ocorrência do Segmento continuam respondendo `404`.
- [ ] Testes HTTP cobrem edição e exclusão de exceção preservada, para Recorrência comum e para Parcelamento.
- [ ] `mvnw verify -B` passa.
