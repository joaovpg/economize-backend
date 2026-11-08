# 02: Consultar séries existentes com configuração estruturada

**What to build:** Permitir consultar séries e parcelas existentes com configuração estruturada editável, identidade, versão e histórico, preservando o comportamento financeiro dos dados já gravados.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

**Referências na spec** (`spec.md`):
- Configuração estruturada de repetição
- `GET /api/transacoes`
- Identidade, versão e concorrência
- Estados de edição e respostas
- Compatibilidade e migração

**Contrato essencial:**
- Objeto `repeticao` com exatamente `frequencia`, `intervalo`, `diasSemana`, `diasMes`, `politicaData`, `termino` e `parcelamento`. Os arrays estão sempre presentes; `diasSemana` é ordenado de SEGUNDA a DOMINGO e `diasMes` em ordem crescente.
- Objeto `serie` com `grupoId`, `segmentoId`, `inicioSegmento`, `dataOriginal`, `numeroParcela` e `totalParcelas` (os dois últimos nulos para repetição sem parcelas).
- Cada item traz `origem`, `protegida`, `escoposDisponiveis`, `chaveEdicao` e `versaoEdicao`, inclusive para ocorrências virtuais e materializadas.
- Migration incremental com `politicaData` por segmento e revisão de série. Número: a próxima após V7; conferir migrations concorrentes da branch antes de criar.
  - Preenchimento: `RECORRENCIA` vira `PADRAO`; `PARCELAMENTO` vira `AJUSTAR_ULTIMO_DIA_MES`.
  - Não alterar RRULE, início, fim, contagem, numeração, supressões, instantâneos ou valores.
  - A revisão de série é inicializada de forma determinística.
- Segmento cujo início não é o primeiro dia da semana ou do mês: devolver `inicioSegmento` e a configuração reais, sem recalcular.
- Mensal antigo sem `BYMONTHDAY`: devolver `diasMes: []` com `PADRAO`. O PUT aceita essa forma somente para segmentos já existentes. Novas séries com `PADRAO` continuam exigindo dias.

**Estado observado no código** (em 2026-11-08):
- Migrations existentes: V1 a V7. Não existe migration de política por segmento nem de revisão de série.
- O motor de recorrência já existe em `recorrencia/` (`ExpansorRecorrencia`, `PoliticaDataOcorrencia`). O leitor atual deve ser usado para verificar que a tradução para a configuração estruturada não perde nenhum caso válido.

**Critérios de aceite:**
- [ ] Migration incremental adiciona política de data por segmento e revisão compartilhada, inicializadas conforme a especificação, preservando regras, datas, supressões, parcelas, valores e histórico.
- [ ] Teste de migration parte do schema anterior e compara ocorrências, saldos e metadados de série indefinida, exceção deslocada, supressão, parcelas, múltiplos segmentos e efetivação.
- [ ] A leitura devolve os campos completos, chave e versão opacas, origem, proteção, escopos e metadados de série, inclusive para ocorrências virtuais e materializadas, respeitando propriedade.
- [ ] A configuração estruturada traduz sem perda as regras existentes, inclusive início não primeiro na semana ou mês e mensal antiga ancorada sem dias; preserva início e política reais.
- [ ] Consulta, filtros e saldo de abertura permanecem corretos em períodos próximos e distantes, inclusive séries indefinidas, sem materializar ocorrências durante a leitura nem expandir indefinidamente.

**Fora de escopo:** escritas novas (issues 03 em diante) e remoção das rotas antigas (issue 08).

**Como verificar:**
- Teste de migration no padrão de schemas isolados já usado pelo projeto, partindo de V7.
- Teste HTTP com `@QuarkusTest` e REST Assured para a leitura, com séries indefinidas e distantes.
