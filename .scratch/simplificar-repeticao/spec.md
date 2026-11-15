# Simplificar a repetição e a edição de ocorrências

Status: ready-for-agent

## Objetivo e contexto

Esta especificação altera dois pontos da spec `.scratch/contratos-transacoes-unificados/spec.md`. As demais regras daquela spec continuam valendo: identidade, versão, prévia, sobreposições, supressões e `ESTA_E_AS_PROXIMAS`.

1. **Configuração de repetição.** O contrato atual expõe quase toda a RRULE ao cliente: frequência, intervalo, `diasSemana`, `diasMes`, `politicaData`, `termino` e `parcelamento`. Isso gera cerca de quinze regras de combinação (por exemplo, "`SEMANAL` exige `diasSemana` contendo o dia da data de início" e "`termino` é nulo se e somente se houver `parcelamento`") e permite enviar combinações contraditórias. O uso pretendido é mais simples: repetir a cada X dias, semanas, meses ou anos; terminar nunca, numa data, após N ocorrências ou ao fim de um Parcelamento ("parcela X de Y"); e, no mensal, repetir no N-ésimo dia útil, como no caso de um salário pago no 5º dia útil.

2. **Edição de uma única ocorrência.** Hoje, uma edição `SOMENTE_ESTA` sempre tira a ocorrência da série: ela vira uma Transação simples destacada ou o início de uma nova série. Até efetivar o aluguel do mês faz isso. A ocorrência deve continuar na série.

As ocorrências virtuais continuam sendo a base do modelo. Elas permitem visualizar os meses futuros de séries indefinidas sem gravar registros, o que não seria viável criando todas as ocorrências como físicas.

## User stories

1. Como Usuário, quero repetir uma receita ou despesa a cada X dias, semanas, meses ou anos, a partir da data financeira escolhida.
2. Como Usuário, quero que uma despesa do dia 31 apareça em todos os meses, no último dia quando o mês for mais curto.
3. Como Usuário, quero marcar que a repetição mensal cai no "N-ésimo dia útil", com N calculado a partir da data escolhida.
4. Como Usuário, quero escolher se a repetição é indefinida, termina numa data, termina após N ocorrências ou é um Parcelamento que começa na parcela X de Y.
5. Como Usuário, quero efetivar ou corrigir uma única ocorrência sem que ela saia da série.
6. Como Usuário, quero visualizar meses futuros com as ocorrências previstas para acompanhar meu orçamento.

---

# Parte 1: Configuração de repetição

## Contrato

`repeticao: null` continua criando uma Transação simples. Quando presente, `repeticao` contém exatamente os campos abaixo. Campos desconhecidos são rejeitados com `400 DADOS_INVALIDOS`.

```json
"repeticao": {
  "frequencia": "MENSAL",
  "intervalo": 1,
  "ancoragem": "DIA_DO_MES",
  "termino": { "tipo": "INDEFINIDO" }
}
```

| Campo | Valores | Regra |
|---|---|---|
| `frequencia` | `DIARIA`, `SEMANAL`, `MENSAL`, `ANUAL` | obrigatório |
| `intervalo` | inteiro de 1 a 2.147.483.647 | obrigatório; "a cada X" |
| `ancoragem` | `DIA_DO_MES`, `DIA_UTIL` | obrigatório; `DIA_UTIL` só com `MENSAL` |
| `termino` | objeto discriminado por `tipo` | obrigatório; ver abaixo |

`termino`:

| `tipo` | Campos | Regra |
|---|---|---|
| `INDEFINIDO` | nenhum | sem fim |
| `EM_DATA` | `data` | data final inclusiva, não anterior à `dataFinanceira` |
| `APOS_OCORRENCIAS` | `quantidade` | inteiro ≥ 1, conta a primeira ocorrência |
| `PARCELAS` | `numeroPrimeiraParcela`, `totalParcelas` | inteiros com `1 ≤ numeroPrimeiraParcela ≤ totalParcelas` |

Cada tipo aceita somente os próprios campos. `PARCELAS` gera `totalParcelas − numeroPrimeiraParcela + 1` ocorrências numeradas de `numeroPrimeiraParcela/totalParcelas` até `totalParcelas/totalParcelas`, e cria um Grupo de recorrência do tipo `PARCELAMENTO`. Os demais tipos criam um grupo do tipo `RECORRENCIA`. O valor informado é sempre o valor de cada ocorrência.

Os campos `diasSemana`, `diasMes`, `politicaData` e `parcelamento` deixam de existir na entrada e na leitura.

## Cálculo das datas

A `dataFinanceira` informada é sempre a primeira ocorrência e define a âncora da série.

- **`DIARIA`:** a cada `intervalo` dias.
- **`SEMANAL`:** a cada `intervalo` semanas, no dia da semana da `dataFinanceira`.
- **`MENSAL` com `DIA_DO_MES`:** a cada `intervalo` meses, no dia do mês da `dataFinanceira`. Quando esse dia não existe no mês (29, 30 ou 31), usa o último dia do mês, sem pular o mês. O ajuste não altera a âncora: uma série do dia 31 volta ao dia 31 nos meses que o têm.
- **`ANUAL`:** a cada `intervalo` anos, no mesmo dia e mês da `dataFinanceira`. Uma série iniciada em 29/02 usa 28/02 nos anos não bissextos.
- **`MENSAL` com `DIA_UTIL`:** a cada `intervalo` meses, no N-ésimo dia útil do mês. N é a posição da `dataFinanceira` entre os dias úteis do seu mês. Nesta entrega, dia útil é de segunda a sexta; feriados não são considerados.

Regras do `DIA_UTIL`:

1. A `dataFinanceira` precisa ser de segunda a sexta. Caso contrário, retorna `422 REPETICAO_INVALIDA`.
2. N precisa ser no máximo 20, porque todo mês tem pelo menos 20 dias de segunda a sexta. Caso contrário, retorna `422 REPETICAO_INVALIDA`. O front deve esconder a opção nessas datas.
3. O cliente não envia N. O servidor calcula N e o devolve na leitura.

## Leitura

`GET`, a resposta de criação e a resposta de edição devolvem `repeticao` no mesmo formato da entrada, acrescido de `numeroDiaUtil`. Esse campo vem preenchido com N quando `ancoragem` é `DIA_UTIL` e `null` nos demais casos. O cliente pode reenviar o objeto lido sem `numeroDiaUtil`.

`serie.numeroParcela` e `serie.totalParcelas` continuam com o significado atual.

## Edição com `ESTA_E_AS_PROXIMAS`

O PUT e a prévia de edição recebem o mesmo formato de `repeticao`.

- Com nova repetição, a `dataFinanceira` da ocorrência selecionada é a âncora do novo trecho. Reenviar a mesma configuração mantém a mesma cadência, inclusive o mesmo N do dia útil, salvo se a data financeira tiver sido deslocada.
- Como a âncora é sempre a primeira ocorrência, deixa de existir a diferença entre validar a criação (início igual à primeira ocorrência) e validar a edição (início igual a alguma ocorrência).
- Conversões entre recorrência e Parcelamento passam a ser uma troca de `termino.tipo` para ou de `PARCELAS`.

## Erros

- `422 REPETICAO_INVALIDA`: `intervalo` fora do limite, `DIA_UTIL` fora de `MENSAL`, data de dia útil inválida ou com N > 20, `EM_DATA` anterior ao início, `quantidade` < 1, ou campos de outro tipo de `termino`.
- `422 PARCELAMENTO_INVALIDO`: numeração de `PARCELAS` inválida.
- `400 DADOS_INVALIDOS`: campo obrigatório ausente, campo desconhecido ou valor de enum inválido.

## Persistência

O Segmento de recorrência continua guardando a RRULE e a política de data, e o schema atual deve bastar. O mapeamento esperado é:

- `DIA_DO_MES` mensal ou anual: `FREQ=MONTHLY|YEARLY;INTERVAL=x`, sem `BYMONTHDAY`, com política `AJUSTAR_ULTIMO_DIA_MES` (expansão já existente em `ExpansorRecorrencia`).
- `DIARIA` e `SEMANAL`: `FREQ=DAILY|WEEKLY;INTERVAL=x` (com `BYDAY` igual ao dia da âncora no semanal) e política `PADRAO`.
- `DIA_UTIL`: `FREQ=MONTHLY;INTERVAL=x;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=n` e política `PADRAO`, expandida pelo iCal4j atrás do port `MotorRecorrencia` (ADR 0009).

## Segmentos existentes

A base de testes não tem dados a preservar. A base de produção tem dados reais: até onde se sabe, Parcelamentos mensais e recorrências indefinidas. Ela está na V7 e foi alimentada pelo contrato antigo `/api/recorrencias`, que aceitava vários `diasMes` e `diasSemana` e exigia `diasMes` no mensal. A migração precisa preservar exatamente as datas já projetadas e nunca descartar dados.

**Antes do deploy:** rodar em produção `.scratch/simplificar-repeticao/diagnostico-segmentos.sql`. A consulta é somente leitura, funciona a partir da V6, devolve apenas contagens e classifica cada segmento assim:

- `OK`: representável no novo contrato com exatamente as mesmas datas.
- `MUDA_DATAS`: mensal com início nos dias 29 a 31 e política `PADRAO` ou `BYMONTHDAY` informado (a expansão anterior só ajustava ao último dia sem `BYMONTHDAY`), ou anual em 29/02 com política `PADRAO`. Nesses casos, a regra atual pula os meses sem esse dia, e o ajuste para o último dia acrescentaria ocorrências, inclusive no passado, alterando saldos.
- `NAO_REPRESENTAVEL`: vários dias, dia diferente do início ou partes de RRULE fora do contrato.

**Migration incremental (V11):**

1. **Verificação:** se existir algum segmento `MUDA_DATAS` ou `NAO_REPRESENTAVEL`, a migration lança erro (`RAISE EXCEPTION`) informando a quantidade. O Flyway desfaz a transação da V11, nada é alterado por ela e o deploy para. Esses segmentos exigem decisão manual antes de seguir.
2. **Normalização dos segmentos `OK` para a forma canônica**, sem mudar nenhuma data:
   - remover `BYMONTHDAY` dos mensais, porque ele é igual ao dia do início;
   - trocar a política dos mensais e anuais `PADRAO` para `AJUSTAR_ULTIMO_DIA_MES`. Isso não muda nenhuma data, porque o início é no máximo dia 28, ou não é 29/02 no anual;
   - manter o `BYDAY` único dos semanais, que já é o dia do início;
   - diários e semanais ficam com `PADRAO`.
3. Não alterar início, fim, contagem, numeração, supressões, transações físicas nem valores.

**Teste da migration**, no padrão de schemas isolados: migrar até V10, inserir um Parcelamento mensal, uma recorrência mensal indefinida com `BYMONTHDAY`, uma semanal, um segmento cortado com ocorrência física e supressão, e um segmento de cada classe bloqueante. Depois, aplicar V11 e verificar que:

- as ocorrências e os saldos são iguais antes e depois para os segmentos `OK`;
- a presença de um segmento bloqueante faz a migration falhar sem alterar nada.

**Deploy em produção:** o mesmo deploy aplica V8 a V11 e remove as rotas antigas de escrita de recorrência. O frontend de produção precisa ser atualizado junto.

---

# Parte 2: Edição de uma única ocorrência

## Problema

`AlterarOcorrenciaSerie` tem dois destinos possíveis, e nenhum mantém a ocorrência na série:

- **`repeticao: null`:** destaca a ocorrência como Transação simples (`origem: TRANSACAO_SIMPLES`, `repeticao: null`, `escoposDisponiveis: []`) e grava uma Supressão.
- **`repeticao` preenchida:** grava uma Supressão e cria uma nova série a partir da data. A série original continua.

Consequências:

1. Efetivar uma ocorrência, que é a ação mais comum, tira a ocorrência da série. Depois de um ano pagando o aluguel, a série tem 12 ocorrências destacadas e 12 supressões.
2. Um cliente que reenvia a `repeticao` lida no GET duplica a série: a nova série e a original projetam as mesmas datas a partir do mês seguinte.
3. A criação materializa a primeira ocorrência efetivada vinculada à série (`CriarTransacao`), mas a edição destaca. As duas operações se comportam de formas diferentes.
4. O glossário define a ocorrência individualizada (física e ainda vinculada), mas nenhum caminho a cria: `setExcecaoRecorrencia(true)` não é chamado no código.

## Regra

- A ocorrência é **virtual** enquanto segue exatamente a regra do Segmento de recorrência.
- Na primeira alteração explícita com `SOMENTE_ESTA`, como efetivar, mudar valor, data, conta, categoria ou descrição, a ocorrência vira **física e continua vinculada**. A `Transacao` passa a ter grupo, segmento, `identificadorRecorrencia` igual à Data original e `excecaoRecorrencia = true`.
- Uma ocorrência física já vinculada é alterada no próprio registro. Ela nunca volta a ser virtual, nem quando é replanejada.
- Em `SOMENTE_ESTA`, o PUT recebe a `repeticao` exatamente como foi lida, sem `numeroDiaUtil`. Qualquer configuração diferente, inclusive `null`, responde `422 ESCOPO_INVALIDO`. Conversões para Transação simples ou para nova regra ficam restritas a `ESTA_E_AS_PROXIMAS`.
- Nenhuma Supressão é gravada nessa edição. O registro físico substitui a ocorrência virtual na projeção pela identidade segmento + data original.
- A revisão da série é incrementada, conforme a ADR 0014.
- A leitura devolve a ocorrência com `origem` `TRANSACAO_RECORRENTE` ou `PARCELA`, `protegida: true`, `serie` e `repeticao` preenchidos e numeração de parcela preservada.
- A ocorrência física continua protegida em `ESTA_E_AS_PROXIMAS` aplicado a partir de uma ocorrência anterior. Ela só pode ser alterada quando é a própria ocorrência selecionada.
- A exclusão com `SOMENTE_ESTA` não muda: remove o registro físico, se existir, e grava a Supressão.

A matriz de conversões da spec de contratos unificados perde as linhas "Série → Simples, `SOMENTE_ESTA`" e "Série → Nova repetição ou parcelamento, `SOMENTE_ESTA`".

---

## Documentação a atualizar

- `.scratch/contratos-transacoes-unificados/spec.md`: registrar no início que a configuração de repetição e a edição `SOMENTE_ESTA` foram substituídas por esta spec, e remover as duas linhas `SOMENTE_ESTA` da matriz de conversões.
- `CONTEXT.md`:
  - Ocorrência recorrente, Exceção recorrente e Conversão de ocorrência: registrar que `SOMENTE_ESTA` individualiza sem destacar e que as conversões usam `ESTA_E_AS_PROXIMAS`;
  - acrescentar Ancoragem e Dia útil.
- `docs/modelo-financeiro.md`: novo formato de repetição e mapeamento para RRULE.
- ADR 0014: a edição `SOMENTE_ESTA` não grava Supressão, porque o registro vinculado ocupa a identidade.
- ADR 0015: mudar o status para "Aceita" quando a Parte 1 for implementada.

## Fora de escopo

- Feriados nacionais, estaduais ou municipais no cálculo do dia útil.
- "Último dia útil do mês". O enum `ancoragem` foi escolhido para permitir incluir esse caso depois sem quebrar o contrato.
- Repetições com vários dias, como "dias 5 e 20" ou "segunda, quarta e sexta". O Usuário cria uma série para cada dia.
- Voltar uma ocorrência física para virtual.
- Mudanças no frontend.

## Testes

Testes HTTP com `@QuarkusTest` e PostgreSQL real.

**Parte 1:**

- Cada frequência com `DIA_DO_MES`, inclusive os dias 29 a 31 e 29/02 no anual.
- `DIA_UTIL`: cálculo de N, data em fim de semana rejeitada, N = 20 aceito e N = 21 rejeitado, intervalo maior que 1, `numeroDiaUtil` na leitura e reenvio do objeto lido.
- Cada tipo de término, com campos de outro tipo rejeitados.
- `PARCELAS` com numeração e quantidade restante.
- Edição `ESTA_E_AS_PROXIMAS` reenviando a configuração lida e trocando entre recorrência e Parcelamento.
- Rejeição de `diasSemana`, `diasMes`, `politicaData` e `parcelamento` como campos desconhecidos.
- Migration V11 conforme "Segmentos existentes".
- OpenAPI consistente com o novo formato.

**Parte 2:**

- Efetivar uma ocorrência virtual cria um registro vinculado, sem Supressão, e a série continua sem duplicar a ocorrência.
- Alterar valor, data financeira ou descrição cria um registro vinculado. Uma nova edição altera o mesmo registro.
- A data financeira alterada não muda a identidade, e a ocorrência não aparece duas vezes.
- Replanejar uma ocorrência efetivada limpa `efetivadoEm`, mantém o registro vinculado e preserva a numeração de parcela.
- `repeticao` diferente da lida, inclusive `null`, em `SOMENTE_ESTA` responde `422 ESCOPO_INVALIDO` sem gravar nada.
- `ESTA_E_AS_PROXIMAS` iniciado antes não altera nem remove a ocorrência física.
- Versão obsoleta responde `409 VERSAO_DESATUALIZADA`.

Executar `.\mvnw.cmd verify -B` antes de concluir.
