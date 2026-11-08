# 04: Converter transação simples em repetição ou parcelamento

**What to build:** Permitir transformar uma transação simples existente na primeira ocorrência de uma repetição ou parcelamento, sem recadastrar nem duplicar o movimento.

**Blocked by:** 03: Cadastrar repetição ou parcelamento pela transação.

**Status:** ready-for-agent

**Referências na spec** (`spec.md`):
- Contratos HTTP: `PUT /api/transacoes/{chaveEdicao}` e a matriz de conversão (linha "Simples → Repetição, parcelada ou não")
- Identidade, versão e concorrência
- Estados de edição e respostas
- Erros e segurança

**Contrato essencial:**
- `PUT /api/transacoes/{chaveEdicao}` em uma transação simples, com destino repetição ou parcelamento. `escopo` é omitido, porque a origem é simples.
- A transação existente vira a primeira ocorrência. Não há cópia nem duplicação.
- Situação e instante de efetivação são preservados quando não alterados. Quando alterados, seguem as regras de efetivação (data não futura no fuso do usuário) e replanejamento (limpa o instante).
- A resposta traz a chave e a versão vigentes, já utilizáveis. Se a identidade foi substituída, a chave antiga responde `404 RECURSO_NAO_ENCONTRADO`.
- Versão obsoleta: `409 VERSAO_DESATUALIZADA`, sem dados financeiros.
- Toda a conversão é atômica: falha não deixa transação parcialmente convertida.

**Questão em aberto (decidir antes de implementar):**
- A spec define colisão de sobreposição entre candidatas e ocorrências protegidas de uma série existente. Uma transação simples não pertence a série, então pode não haver colisão nesta conversão. A spec também diz que o par `politicaSobreposicao` + `resolucoesSobreposicao` é obrigatório quando a edição "cria um novo trecho". Falta dizer se converter simples em série conta como novo trecho. Enquanto isso não for definido, a issue deve ser tratada como bloqueada nesse ponto.

**Estado observado no código** (em 2026-11-08):
- Depende da issue 03, que ainda não existe. Não há caminho de conversão no código.

**Critérios de aceite:**
- [ ] PUT converte uma transação simples em repetição ou parcelamento com a configuração e validações do contrato, preservando o movimento existente como primeira ocorrência.
- [ ] Situação e instante de efetivação são preservados quando não alterados e seguem as regras de efetivação e replanejamento quando alterados.
- [ ] A resposta fornece item completo e chave e versão vigentes imediatamente utilizáveis; uma identidade substituída não permite edição pela chave antiga.
- [ ] Versão obsoleta, propriedade, regras financeiras e eventuais decisões de sobreposição são revalidadas atomicamente; falha não deixa conversão parcial.
- [ ] Testes HTTP cobrem ambos os destinos (repetição e parcelamento), consulta, saldo e ausência de duplicação.

**Fora de escopo:** prévia de sobreposições e alteração de séries existentes (issues 05 e 06).

**Como verificar:**
- Testes HTTP com `@QuarkusTest` e PostgreSQL real: conversão para cada destino, consulta, saldo, chave antiga retornando 404 e falha com rollback.
