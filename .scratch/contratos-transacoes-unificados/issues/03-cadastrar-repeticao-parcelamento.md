# 03: Cadastrar repetição ou parcelamento pela transação

**What to build:** Permitir criar uma receita ou despesa com repetição opcional ou parcelamento e consultar suas ocorrências e saldos pelo fluxo unificado.

**Blocked by:** 01: Unificar cadastro e edição de transações simples; 02: Consultar séries existentes com configuração estruturada.

**Status:** done

**Referências na spec** (`spec.md`):
- Contratos HTTP: `POST /api/transacoes`
- Configuração estruturada de repetição (inclui as combinações válidas)
- Estados de edição e respostas (criação responde com o item do GET)
- Erros e segurança
- Exemplo de criação de série mensal indefinida

**Contrato essencial:**
- `repeticao: null` cria transação simples (já existe). Não nulo cria série ou parcelamento.
- `frequencia`: `DIARIA`, `SEMANAL`, `MENSAL` ou `ANUAL`. `intervalo`: inteiro de 1 a 2.147.483.647.
- `diasSemana` usa `SEGUNDA`, `TERCA`, `QUARTA`, `QUINTA`, `SEXTA`, `SABADO`, `DOMINGO`. `diasMes` usa inteiros de 1 a 31. Arrays sem duplicatas e sempre presentes.
- Combinações:
  - `DIARIA`: ambos os arrays vazios.
  - `SEMANAL`: `diasSemana` não vazio, `diasMes` vazio; inclui o dia da semana de `dataFinanceira`.
  - `MENSAL` com `PADRAO` e sem parcelamento: `diasMes` não vazio, incluindo o dia de `dataFinanceira`.
  - `MENSAL` com parcelamento: ambos os arrays vazios; cadência ancorada no dia inicial.
  - `ANUAL`: ambos os arrays vazios; mês e dia vêm de `dataFinanceira`.
  - `AJUSTAR_ULTIMO_DIA_MES`: só para `MENSAL` ou `ANUAL` com ambos os arrays vazios. Não combina com múltiplos dias.
  - Parcelamento semanal: um único dia da semana, o de `dataFinanceira`.
- Início: em semana ou mês com vários dias, nenhum dia pode ser anterior ao dia de `dataFinanceira`.
- `politicaData` (`PADRAO` ou `AJUSTAR_ULTIMO_DIA_MES`) é explícito na entrada e devolvido na leitura. A interface pode sugerir valores, mas o servidor não infere.
- `termino`: `{"tipo":"INDEFINIDO"}`, `{"tipo":"EM_DATA","data":"yyyy-MM-dd"}` (data inclusiva) ou `{"tipo":"APOS_OCORRENCIAS","quantidade":n}` (n > 0). Com `parcelamento`, `termino` é `null`.
- `parcelamento`: `{"numeroPrimeiraParcela":p,"totalParcelas":t}`, inteiros positivos, com `p <= t`. Restantes = `t - p + 1`. O valor informado é por parcela. A numeração não muda após supressões.
- `situacao` vale somente para a primeira ocorrência. Se `EFETIVADA`, registrar o instante e materializar a primeira ocorrência sem cópia virtual. As demais são `PLANEJADA`.
- `201 Created` com `Location`, `chaveEdicao`, `versaoEdicao` e o item completo, com valor assinado. A primeira ocorrência virtual é identificada por segmento e data original, sem UUID de transação.
- Erros: `422 REPETICAO_INVALIDA` (frequência, dias, política, início ou término incompatíveis); `422 PARCELAMENTO_INVALIDO` (numeração, total ou combinação com término); `400 DADOS_INVALIDOS` (campos desconhecidos ou ausentes).

**Estado observado no código** (em 2026-11-08):
- `CriarTransacao.validar` (`CriarTransacao.java:84-86`) rejeita toda repetição com `REPETICAO_INVALIDA`. É essa validação que esta issue substitui.
- As regras de expansão e de política de data já existem no módulo `recorrencia/` (`CriarRecorrencia`, `CriarParcelamento`, `ExpansorRecorrencia`, `PoliticaDataOcorrencia`). A operação unificada deve reutilizar essas regras sem duplicá-las, como manda a spec.

**Caso de referência:** o payload do aluguel mensal (`diasMes: [5]`, `PADRAO`, `termino` `INDEFINIDO`, `parcelamento: null`) é um caso válido desta issue e deve virar teste.

**Critérios de aceite:**
- [ ] POST aceita frequência, intervalo, dias, política de data e término estruturados ou parcelamento, com validação estrita das combinações, início e limites previstos.
- [ ] Término indefinido, em data inclusiva e após ocorrências, além da numeração e quantidade restante de parcelas, produzem as projeções corretas; supressões não renumeram posições.
- [ ] Ambas as políticas de data funcionam nas combinações permitidas, incluindo dias 29 a 31, e a configuração devolvida mantém seu significado.
- [ ] Situação aplica-se somente à primeira ocorrência; quando efetivada, ela é materializada uma vez, com instante, sem duplicação virtual; futuras permanecem planejadas.
- [ ] Resposta 201 contém Location e o item completo de leitura com impacto assinado, chave e versão utilizáveis, inclusive para primeira ocorrência virtual.
- [ ] Testes HTTP verificam regras inválidas, propriedade, conta e categoria, valores, saldos, isolamento e atomicidade.
- [ ] Cada combinação da tabela de frequências tem pelo menos um teste válido e um inválido, com o código de erro correto.

**Fora de escopo:** PUT e conversões (issues 04 a 06), transferências (não se repetem, conforme a spec) e remoção das rotas antigas (issue 08).

**Como verificar:**
- `.\mvnw.cmd -Dtest=TransacaoResourceTest test` e, para um caso isolado, `.\mvnw.cmd -Dtest=TransacaoResourceTest#metodo test`.
- Antes de concluir: `.\mvnw.cmd verify -B`.

## Comments

Concluída na verificação de 2026-11-08 sobre `edacff1`: `verify` passa com 205 testes; `CriarRepeticaoTransacaoResourceTest` tem 68 testes cobrindo combinações válidas e inválidas (`rejeitaCombinacoesInvalidasComOCodigoDaEspecificacao`), término em data e após ocorrências, numeração de parcelas, ajuste de dias 29–31, efetivação materializada uma única vez, propriedade e atomicidade. A conferência foi feita pelos nomes e pelo resultado dos testes, não por revisão linha a linha da matriz completa.
