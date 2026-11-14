# 05: Editar e converter uma única ocorrência

**What to build:** Permitir editar explicitamente uma ocorrência e convertê-la em transação simples ou início de outra repetição ou parcelamento, enquanto a série original continua.

**Blocked by:** 03: Cadastrar repetição ou parcelamento pela transação.

**Status:** done

**Referências na spec** (`spec.md`):
- Contratos HTTP: `PUT /api/transacoes/{chaveEdicao}` (escopo) e as linhas "Série → Simples, `SOMENTE_ESTA`" e "Série → Nova repetição ou parcelamento, `SOMENTE_ESTA`" da matriz
- `situacao` (aplica-se apenas à ocorrência selecionada)
- Identidade, versão e concorrência
- Estados de edição e respostas (`protegida`, `serie`, `escoposDisponiveis`)

**Contrato essencial:**
- `escopo: "SOMENTE_ESTA"`, em português e sem alias ingleses.
- Aplica-se a ocorrência virtual, individualizada e efetivada. Uma ocorrência efetivada mantém o instante enquanto continuar efetivada.
- A identidade da ocorrência é segmento + data original. Alterar a data financeira não muda a identidade.
- **Individualizar como simples:** a ocorrência original não reaparece (supressão durável), e a série continua. O item resultante tem `origem: TRANSACAO_SIMPLES`, `repeticao: null`, `escoposDisponiveis: []` e `protegida: true`, mantendo `serie` como histórico.
- **Iniciar nova série:** a ocorrência selecionada inicia uma nova série. A origem é suprimida para não reaparecer. A série anterior continua.
- **Revisão:** o grupo afetado tem a revisão incrementada. Se a ocorrência destacada mantém vínculo histórico, a revisão da origem também muda enquanto isso puder afetar colisões ou a prévia.
- Quando origem e destino são grupos diferentes, bloquear e revisar os dois em ordem determinística de UUID.
- Erros: `422 ESCOPO_INVALIDO` quando o escopo não se aplica à ocorrência; `409` e `404` seguem as regras gerais.

**Estado observado no código** (em 2026-11-08):
- Não há `SOMENTE_ESTA` nem `ESTA_E_AS_PROXIMAS` no código. O escopo ainda não existe.

**Critérios de aceite:**
- [ ] SOMENTE_ESTA atende ocorrências virtuais, individualizadas e efetivadas; a alteração preserva o instante enquanto permanecerem efetivadas.
- [ ] Converter em simples ou iniciar outra série impede o reaparecimento da ocorrência original e preserva o histórico de origem e a numeração aplicável.
- [ ] A data original identifica a ocorrência mesmo quando sua data financeira muda; a leitura distingue ocorrência destacada simples e ocorrência ainda associada e informa proteção e escopos reais.
- [ ] Revisões dos grupos afetados, inclusive origem histórica relevante para prévias, são incrementadas atomicamente; bloqueios de dois grupos seguem ordem determinística.
- [ ] Resposta devolve chave e versão utilizáveis, e falhas de propriedade, versão ou validação não deixam gravações parciais; testes cobrem projeção, materialização, supressão e saldo.

**Fora de escopo:** `ESTA_E_AS_PROXIMAS`, prévia e resolução de sobreposições (issue 06).

**Como verificar:**
- Testes HTTP cobrindo ocorrência virtual, individualizada e efetivada, com verificação de projeção, materialização, supressão e saldo.

## Comments

**Feito**
- `PUT /api/transacoes/{chaveEdicao}` aceita `escopo: "SOMENTE_ESTA"` para ocorrência de série (chave `1:r:`), virtual, individualizada ou efetivada. Identidade = segmento + data original; a data financeira alterada não muda a identidade.
- Destino simples (`repeticao: null`): a transação vira simples sem vínculo com grupo/segmento, com origem preservada em `ID_SEGMENTO_ORIGEM`, `DAT_ORIGEM_RECORRENCIA` e `INT_NUMERO_PARCELA_ORIGEM` (migration criada como V9 e renomeada para V10 após mesclar a integração). Resultado: `origem TRANSACAO_SIMPLES`, `repeticao null`, `escoposDisponiveis []`, `protegida true`, `serie` como histórico. A ocorrência original fica suprimida (`TB008`) e a série segue.
- Destino nova série (`repeticao` preenchida): a ocorrência original é suprimida, a linha materializada da origem é removida e a nova série é criada via `CriarSerieTransacao`. Se a ocorrência estava efetivada, a primeira ocorrência da nova série mantém `efetivadoEm`.
- Revisão: o grupo de origem é bloqueado e incrementado a cada PUT/DELETE de ocorrência destacada (`AlterarTransacao` e `ExcluirTransacao` também incrementam a revisão da origem quando a simples tem `segmentoOrigem`).
- Versão obsoleta: 409 `VERSAO_DESATUALIZADA`, comparando a revisão do grupo mais a versão do registro, sob bloqueio pessimista do grupo antes de qualquer gravação. Falhas de validação não deixam gravação parcial (testado).
- Ao converter ocorrência destacada (simples com `segmentoOrigem`) em série, os campos de origem são limpos para não violar a constraint `CK006_07`; a identidade passa a ser a da nova série.
- Ocorrência destacada editada ou excluída pela rota simples incrementa a revisão do grupo de origem.
- Testes: `AlterarOcorrenciaSerieResourceTest` (12 casos HTTP: projeção, materialização, supressão, saldo, efetivada, nova série, versão, propriedade, escopo, falha sem gravação, parcelas, exclusão de destacada) e `DestacarOcorrenciaSerieMigrationTest` (constraints da migration).

**Decisões tomadas sem cobertura explícita do ticket**
- Seams: confirmados pelo implementador (HTTP `PUT`/`GET`/`DELETE`), sem confirmação prévia por execução não interativa.
- Nova série não mantém vínculo histórico com a origem: a linha materializada é removida e a origem fica apenas suprimida.
- Moeda: no destino simples aplica-se a regra de moeda de transação simples; na nova série, a regra de criação (sem restrição de moeda).
- Grupo recém-criado da nova série não é bloqueado nem revisado (não existe antes da operação); sua revisão começa em 0.

**Não atendido (fora deste ticket)**
- `ESTA_E_AS_PROXIMAS`: rejeitado com 422 `ESCOPO_INVALIDO` até o ticket 06.
- Prévia e resolução de sobreposições: `politicaSobreposicao`/`resolucoesSobreposicao` enviados nesta edição são rejeitados com 422 `RESOLUCAO_SOBREPOSICAO_INVALIDA`. O ticket 06 deve definir o tratamento.
- Documentação de domínio e OpenAPI do escopo: ticket 08.
