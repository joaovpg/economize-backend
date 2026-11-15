# 02: `SOMENTE_ESTA` mantém a ocorrência na série

**What to build:** Ao editar uma única ocorrência de série com `SOMENTE_ESTA`, inclusive para efetivá-la, a ocorrência vira um registro físico que continua vinculado ao Segmento de recorrência, em vez de ser destacada como Transação simples ou iniciar uma nova série.

**Blocked by:** 01: Repetição ancorada na data financeira. Esta issue compara `repeticao` no formato novo.

**Status:** resolved

**Referências na spec** (`spec.md`):
- Parte 2: Edição de uma única ocorrência
- Documentação a atualizar
- Testes, Parte 2

**Contrato essencial:**
- `SOMENTE_ESTA` altera só a ocorrência selecionada e a mantém na série. A ocorrência virtual vira física com grupo, segmento, `identificadorRecorrencia` igual à Data original e `excecaoRecorrencia = true`. Uma ocorrência já física é alterada no próprio registro.
- O PUT `SOMENTE_ESTA` aceita apenas a `repeticao` igual à lida. Qualquer outra, inclusive `null`, responde `422 ESCOPO_INVALIDO`.
- A edição não grava Supressão e incrementa a revisão da série.

**Estado observado no código** (em 2026-11-14):
- `AlterarOcorrenciaSerie.executar` desvia para `individualizarComoSimples` ou `iniciarNovaSerie` conforme `repeticao` (linha 118). Os dois caminhos devem ser substituídos pela alteração vinculada.
- A leitura já suporta ocorrência física vinculada. `ResolverOcorrenciasRecorrentes.materializada` monta o item de série, e `protegida` considera `excecaoRecorrencia || EFETIVADA`.
- `ExcluirOcorrenciaSerie` com `SOMENTE_ESTA` já remove o registro físico e grava a Supressão, e não muda.
- `PreverAlteracaoTransacao` trata `SOMENTE_ESTA` com `repeticao` nula ou preenchida e precisa acompanhar a nova regra.

**Critérios de aceite:**
- [ ] Efetivar uma ocorrência virtual com `SOMENTE_ESTA` cria um registro vinculado, sem Supressão. A série continua projetando as demais datas, sem duplicar a editada.
- [ ] Alterar valor, data financeira ou descrição cria um registro vinculado com `excecaoRecorrencia = true`. Uma nova edição altera o mesmo registro.
- [ ] A data financeira alterada não muda a identidade (segmento + data original), e a ocorrência não aparece duas vezes na consulta.
- [ ] Replanejar uma ocorrência efetivada limpa `efetivadoEm`, mantém o registro vinculado e preserva a numeração de parcela.
- [ ] `repeticao` diferente da lida, inclusive `null`, em `SOMENTE_ESTA` responde `422 ESCOPO_INVALIDO` sem gravar nada.
- [ ] `ESTA_E_AS_PROXIMAS` iniciado antes da ocorrência física não a altera nem a remove.
- [ ] Versão obsoleta responde `409 VERSAO_DESATUALIZADA`, e a revisão da série é incrementada a cada edição.
- [ ] A prévia de edição acompanha a nova regra.
- [ ] A documentação da Parte 2 em "Documentação a atualizar" foi atualizada.
- [ ] `.\mvnw.cmd verify -B` passa.

**Fora de escopo:** voltar uma ocorrência física para virtual e frontend.

**Como verificar:**
- `.\mvnw.cmd -Dtest=AlterarOcorrenciaSerieResourceTest test`
- Antes de concluir: `.\mvnw.cmd verify -B`.

## Comments

- 2026-11-14: Implementado no working tree de `feature/contratos-unificados` (sem commit). `SOMENTE_ESTA` cria/altera registro vinculado com `excecaoRecorrencia = true`, sem Supressão. `.\mvnw.cmd verify -B`: 305 testes, BUILD SUCCESS.
