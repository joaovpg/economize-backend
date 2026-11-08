# 06: Alterar esta ocorrência e as próximas com prévia de sobreposições

**What to build:** Permitir alterar uma série a partir de uma ocorrência, visualizar os impactos e resolver sobreposições sem perder histórico nem ocorrências posteriores protegidas.

**Blocked by:** 05: Editar e converter uma única ocorrência.

**Status:** ready-for-agent

**Sugestão de divisão:** esta issue junta três entregas: (a) a prévia, somente leitura; (b) o PUT com resoluções de sobreposição; (c) conversões entre repetição e parcelamento. Considere dividir em 06a (a) e 06b (b e c), para reduzir o tamanho de cada ciclo.

**Referências na spec** (`spec.md`):
- Contratos HTTP: `PUT` com `ESTA_E_AS_PROXIMAS` e a matriz de conversão
- Prévia e sobreposições
- Forma da prévia e do PUT (com exemplos de request e response)
- Identidade, versão e concorrência

**Contrato essencial:**

*Prévia* — `POST /api/transacoes/{chaveEdicao}/previa-edicao`
- Corpo: `{ alteracao, periodo, politicaSobreposicao }`.
  - `alteracao`: representação completa do PUT, sem `resolucoesSobreposicao` e sem `politicaSobreposicao`. Inclui `escopo` (`ESTA_E_AS_PROXIMAS`) e `versaoEdicao`.
  - `periodo`: `inicio` e `fim` em `yyyy-MM`, de 1 a 12 meses civis, inclusivos.
  - `politicaSobreposicao`: somente `MANTER_SOMENTE_PRESERVADA`.
- Resposta `200`: `versaoEdicao`, `periodo`, `politicaSobreposicao`, `itensPreservados`, `ocorrenciasNovas`, `sobreposicoes` e `quantidadeSobreposicoesForaPeriodo`.
- Sobreposição: a data **financeira** de uma candidata coincide com a de uma ocorrência protegida posterior ao corte. A ocorrência selecionada não concorre consigo mesma. A comparação não usa a data original.
- Só são relatadas sobreposições com data financeira dentro do período. `quantidadeSobreposicoesForaPeriodo` é contagem exata, sem expandir repetição infinita.
- Ordenação: candidatos e sobreposições por data financeira e depois data original; chaves preservadas em ordem lexicográfica.
- A prévia não grava nada e não gera token. O PUT recalcula e revalida.

*PUT* — `PUT /api/transacoes/{chaveEdicao}`
- Acrescenta `politicaSobreposicao` e `resolucoesSobreposicao`, um array de `{ "dataOriginalCandidata": "...", "resolucao": "MANTER_SOMENTE_PRESERVADA" | "MANTER_AMBAS" }`.
- Cada data aparece no máximo uma vez e deve ser sobreposição da versão enviada. Array vazio aplica a política geral.
- O par é obrigatório apenas em edição que cria um novo trecho; nas demais, deve ser omitido.
- A resolução se aplica ao candidato inteiro, nunca a uma preservada específica. Preservadas nunca são alteradas.
- `MANTER_SOMENTE_PRESERVADA` suprime o candidato de forma durável; `MANTER_AMBAS` gera os dois impactos.
- Excesso no tamanho do corpo ou da lista de escolhas retorna `400`, sem truncar.

*Conversões:*
- Recorrência → parcelamento: exige número da primeira parcela e total.
- Parcelamento → recorrência: exige término novo e preserva a numeração anterior no histórico.
- Alterar o total cria novo grupo. Datas financeiras de registros protegidos não mudam.

*Erros:* `409 VERSAO_DESATUALIZADA` (prévia ou PUT com versão obsoleta); `422 RESOLUCAO_SOBREPOSICAO_INVALIDA` (decisão duplicada, desconhecida ou data que não é candidata sobreposta); `422 ESCOPO_INVALIDO`.

**Estado observado no código** (em 2026-11-08):
- Depende da issue 05, que ainda não existe. Não há prévia nem resolução de sobreposição no código.

**Critérios de aceite:**
- [ ] ESTA_E_AS_PROXIMAS corta pela data original inclusive para ocorrência selecionada materializada ou efetivada, preserva posteriores individualizadas ou efetivadas e permite destino simples, repetição ou parcelamento.
- [ ] Conversões entre recorrência e parcelamento respeitam término, numeração e total histórico; alteração do total cria novo grupo conforme o modelo, sem alterar automaticamente datas financeiras protegidas.
- [ ] Prévia sem gravação retorna período, versão, itens preservados, candidatas, impactos, decisões e contagem exata fora do período; ordenação e limites seguem a especificação.
- [ ] Sobreposição compara data financeira da candidata com a das protegidas posteriores, exclui a selecionada e trata várias preservadas por candidato; decisões específicas válidas prevalecem sobre a política geral.
- [ ] PUT aplica também a política fora da prévia, persiste supressões duráveis e mantém ambos os impactos quando escolhido; não afeta transações independentes criadas depois.
- [ ] Versão, propriedade, regras financeiras e escolhas são revalidadas sob proteção transacional no PUT; concorrência, prévia obsoleta ou falha geram resposta apropriada sem efeitos parciais.
- [ ] Testes HTTP e PostgreSQL cobrem colisões com datas deslocadas, múltiplas preservadas, ambas as decisões, série indefinida, concorrência e saldos.

**Fora de escopo:** alterar transferências (não se repetem) e qualquer conversão entre transferência e transação.

**Como verificar:**
- Testes HTTP com PostgreSQL real: colisões com datas deslocadas, múltiplas preservadas, ambas as decisões, série indefinida, prévia obsoleta entre prévia e PUT, concorrência entre escritores e saldos.

## Comments

**06a concluído** (prévia, somente leitura). O status deste ticket continua `ready-for-agent` até o 06b.

**Feito**
- `POST /api/transacoes/{chaveEdicao}/previa-edicao`, com corpo `{ alteracao, periodo, politicaSobreposicao }` e resposta 200 com os campos da spec. Serve chave de ocorrência de série (`1:r:`) e chave de transação simples (`1:s:`).
- Caso de uso `PreverAlteracaoTransacao` (`executarOcorrencia` e `executarSimples`). Não grava, não usa lock pessimista, não altera saldos e não gera token.
- Protegidas: registros do grupo com data original igual ou posterior ao corte, individualizados ou efetivados, excluída a própria selecionada. Colisão é igualdade de data financeira. Só é relatada dentro do período. `quantidadeSobreposicoesForaPeriodo` conta datas financeiras distintas fora do período que coincidem com protegidas, testando cada data pontualmente contra a regra, sem expandir a repetição.
- Reuso: `RegrasEdicaoTransacao` (campos, conta, categoria e datas) passou a ser usado também por `AlterarOcorrenciaSerie` e `AlterarTransacao`. A localização de ocorrência saiu de `AlterarOcorrenciaSerie` para `LocalizarOcorrenciaRecorrente`. `RepeticaoValidacao` ficou público. Os PUTs mantêm o comportamento, e as suítes existentes passam.
- Testes: `PreverAlteracaoTransacaoResourceTest` (11 casos HTTP): prévia sem colisão sem gravação, colisão com data deslocada, selecionada que não concorre consigo, várias preservadas com chaves em ordem lexicográfica, contagem fora do período em série indefinida, transação simples, versão obsoleta, período/política/campos inválidos, escopo inválido, propriedade e chave inválida.

**Decisões tomadas sem o ticket cobrir**
- Protegidas consideradas em todo o grupo, não só no segmento selecionado, porque segmentos posteriores também são afetados pelo corte.
- `itensPreservados` e `sobreposicoes` ficam limitados ao período. `itensPreservados` usa o item de leitura (`ItemResponse`).
- A candidata correspondente à selecionada usa como `dataOriginal` a própria data financeira, como em `iniciarNovaSerie`.
- Alteração de série para destino simples aplica a regra de moeda da conversão para simples, como no PUT.
- `politicaSobreposicao` diferente de `MANTER_SOMENTE_PRESERVADA` retorna 400 `DADOS_INVALIDOS` no campo `politicaSobreposicao`, e não 422.
- Escopo ausente, `SOMENTE_ESTA` ou outro valor em ocorrência de série retorna 422 `ESCOPO_INVALIDO`. Escopo informado em transação simples também retorna 422.
- Não há validação de início igual ou posterior à data original, que existia no legado THIS_AND_FUTURE. A spec não exige; decidir no 06b.
- Enum `ResolucaoSobreposicao` criado em `transacao` e usado na requisição e na resposta da prévia. O PUT do 06b deve reutilizá-lo.

**Falta para o 06b**
- PUT com `politicaSobreposicao` e `resolucoesSobreposicao`. Duplicata, resolução desconhecida ou data que não é candidata sobreposta retornam 422 `RESOLUCAO_SOBREPOSICAO_INVALIDA`. O par é obrigatório só ao criar novo trecho.
- `ESTA_E_AS_PROXIMAS` no PUT: corte pela data original, supressão durável dos candidatos suprimidos, preservação das protegidas e revisão do grupo sob bloqueio. A prévia deve ser recalculada sob o mesmo bloqueio.
- Conversões entre recorrência e parcelamento, conforme a matriz da spec, com numeração e novo grupo ao alterar o total.
- Testes de prévia obsoleta entre prévia e PUT, concorrência e saldos.
- Enquanto isso, o PUT continua respondendo 422 `ESCOPO_INVALIDO` para `ESTA_E_AS_PROXIMAS`.
