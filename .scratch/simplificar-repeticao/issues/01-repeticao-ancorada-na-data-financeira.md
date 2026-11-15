# 01: Repetição ancorada na data financeira, com dia útil e parcelas como término

**What to build:** Substituir a configuração de repetição atual pelo formato `frequencia`, `intervalo`, `ancoragem` e `termino` descrito na spec. A mudança vale para criação, PUT e prévia de edição e inclui a ancoragem no N-ésimo dia útil do mês.

**Blocked by:** nenhum. Antes do deploy, rodar `diagnostico-segmentos.sql` em produção (ver "Segmentos existentes" na spec).

**Status:** resolved

**Referências na spec** (`spec.md`, Parte 1):
- Contrato
- Cálculo das datas
- Leitura
- Edição com `ESTA_E_AS_PROXIMAS`
- Erros
- Persistência

**Contrato essencial:**
- `repeticao` aceita exatamente `frequencia`, `intervalo`, `ancoragem` e `termino`. `diasSemana`, `diasMes`, `politicaData` e `parcelamento` passam a ser campos desconhecidos (`400`).
- `termino.tipo` aceita `INDEFINIDO`, `EM_DATA`, `APOS_OCORRENCIAS` e `PARCELAS`. `PARCELAS` substitui o objeto `parcelamento` e define o tipo de grupo `PARCELAMENTO`.
- A `dataFinanceira` é a primeira ocorrência e a âncora da série. No mensal e no anual com `DIA_DO_MES`, uma data inexistente no mês usa o último dia.
- `DIA_UTIL` só vale com `MENSAL`, considera dias de segunda a sexta e calcula N a partir da `dataFinanceira`, com N ≤ 20. A leitura devolve `numeroDiaUtil`.

**Estado observado no código** (em 2026-11-14):
- `RepeticaoTransacaoRequest` e `RepeticaoRecorrencia` carregam `diasSemana`, `diasMes`, `politicaData`, `termino` e `parcelamento`.
- `RepeticaoValidacao` concentra as regras de combinação e separa `validar` (criação) de `validarEdicao`, que também aceita a forma mensal sem dias de segmentos legados. As duas validações devem se reduzir a uma.
- `RegraRecorrencia` gera `BYDAY` e `BYMONTHDAY`, mas não `BYSETPOS`. `LeitorRruleRecorrencia` precisa ler `BYSETPOS` de volta para devolver `DIA_UTIL` e N.
- `ExpansorRecorrencia` já aplica `AJUSTAR_ULTIMO_DIA_MES` em mensal e anual ancorados.
- Os mesmos tipos são usados por `CriarTransacao`, `AlterarOcorrenciaSerie`, `AlterarEstaEAsProximas` e `PreverAlteracaoTransacao`.

**Critérios de aceite:**
- [ ] POST, PUT e prévia aceitam somente o novo formato e rejeitam os campos antigos com `400`.
- [ ] As datas projetadas seguem a seção "Cálculo das datas", inclusive os dias 29 a 31, 29/02 e o N-ésimo dia útil com intervalo maior que 1.
- [ ] `DIA_UTIL` em data de fim de semana, com N > 20 ou fora de `MENSAL` responde `422 REPETICAO_INVALIDA`.
- [ ] Os quatro tipos de término produzem as projeções corretas. `PARCELAS` mantém a numeração e o total, que não mudam após supressões.
- [ ] A leitura devolve `ancoragem`, `termino` e `numeroDiaUtil`, e reenviar o objeto lido num PUT `ESTA_E_AS_PROXIMAS` mantém a mesma cadência.
- [ ] A troca entre recorrência e Parcelamento por `termino.tipo` funciona no PUT.
- [ ] A migration V11 normaliza os segmentos `OK` sem mudar nenhuma data e falha sem alterar nada quando existe segmento `MUDA_DATAS` ou `NAO_REPRESENTAVEL`, com teste de migration no padrão de schemas isolados.
- [ ] O OpenAPI gerado reflete o novo formato; `CONTEXT.md` e `docs/modelo-financeiro.md` são atualizados.
- [ ] `.\mvnw.cmd verify -B` passa.

**Fora de escopo:** feriados, "último dia útil", repetições com vários dias e frontend.

**Como verificar:**
- `.\mvnw.cmd -Dtest=CriarRepeticaoTransacaoResourceTest test`
- Antes de concluir: `.\mvnw.cmd verify -B`.

## Comments

- 2026-11-14: Implementado no working tree de `feature/contratos-unificados` (sem commit). `.\mvnw.cmd verify -B`: 305 testes, BUILD SUCCESS. Revisão corrigiu a classificação da V11 (mensal 29–31 com `BYMONTHDAY` passa a `MUDA_DATAS`) e o estouro de `intervalo` no anual.
