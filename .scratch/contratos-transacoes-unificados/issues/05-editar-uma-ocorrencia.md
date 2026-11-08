# 05: Editar e converter uma única ocorrência

**What to build:** Permitir editar explicitamente uma ocorrência e convertê-la em transação simples ou início de outra repetição ou parcelamento, enquanto a série original continua.

**Blocked by:** 03: Cadastrar repetição ou parcelamento pela transação.

**Status:** ready-for-agent

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
