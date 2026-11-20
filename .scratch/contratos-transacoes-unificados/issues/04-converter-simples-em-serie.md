# 04: Converter transação simples em repetição ou parcelamento

**What to build:** Permitir transformar uma transação simples existente na primeira ocorrência de uma repetição ou parcelamento, sem recadastrar nem duplicar o movimento.

**Blocked by:** 03: Cadastrar repetição ou parcelamento pela transação.

**Status:** done

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

**Decisão sobre a questão de sobreposição (registrada pelo orquestrador):**
- Converter transação simples em repetição ou parcelamento **não** cria "novo trecho": não corta nem substitui uma série existente. Portanto o par `politicaSobreposicao` + `resolucoesSobreposicao` deve ser **omitido**; se enviado nessa edição, rejeitar com `400 DADOS_INVALIDOS`.
- Não há ocorrências protegidas de série a colidir nesta conversão; a regra de sobreposição não se aplica aqui.

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

## Comments

**Implementado:**
- `AlterarTransacao` deixou de rejeitar `repeticao`. Com repetição ou parcelamento, `converterEmSerie` chama `CriarSerieTransacao` (que valida com `RepeticaoValidacao` antes de gravar) e vincula a própria transação ao grupo e segmento criados, com `identificadorRecorrencia` igual à data financeira e `excecaoRecorrencia = false`. Não há cópia. Os campos comuns passaram a ser aplicados por `aplicarDadosComuns`, compartilhado com a edição simples.
- A resposta é o item de leitura da primeira ocorrência materializada, com a chave `chaveRecorrencia` (`1:r:`) e a versão de série vigentes. A chave `1:s:` antiga responde 404, porque a transação agora pertence a uma série.
- `AlterarTransacaoRequest.repeticao` passou de `JsonNode` para `RepeticaoTransacaoRequest` (mesmo contrato do POST); `TransacaoHttpMapper` usa o `toRepeticao` existente.
- Testes: `ConverterTransacaoEmSerieResourceTest` cobre repetição e parcelamento, consulta, saldo, ausência de duplicação, efetivação preservada, planejada para efetivada, efetivada para planejada, data futura, versão obsoleta, escopo e decisões de sobreposição, repetição e parcelamento inválidos, transação alheia e rollback. O rollback injeta falha com trigger PostgreSQL em `UPDATE` de `TB006_TRANSACAO`, porque Mockito não está no classpath. Após a falha não há grupo, segmento nem alteração, e a mesma versão converte com sucesso depois.

**Decisões tomadas sem o ticket cobrir:**
- Conversão com `resolucoesSobreposicao` ou `politicaSobreposicao` responde 400 `DADOS_INVALIDOS`, conforme a decisão do ticket. Na edição simples sem repetição, o comportamento anterior (422 `RESOLUCAO_SOBREPOSICAO_INVALIDA`) foi mantido, porque há teste existente para ele.
- `escopo` informado na conversão responde 422 `ESCOPO_INVALIDO`, como já ocorria.
- A primeira ocorrência planejada fica materializada (não volta a ser virtual), com `protegida: false` e `virtual: false`. A efetivada continua `protegida: true`.

**Pendências dependentes de outras issues:**
- A chave devolvida é `1:r:` (ocorrência de série). O `PUT /api/transacoes/{chaveEdicao}` atual só aceita `1:s:`, então editar essa ocorrência depende do suporte a chaves de série na issue 05.
- Não há validação de que o dia de início pertença à regra mensal ou semanal (spec: "inclui o dia da data de início"). Isso é herdado de `RepeticaoValidacao`, sem alteração nesta issue.
