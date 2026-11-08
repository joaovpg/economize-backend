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
