# ADR 0014: Revisao compartilhada da serie e supressao duravel

## Status

Aceita.

Refina a consequencia da ADR 0012 que dispensava teste dedicado do documento OpenAPI: o contrato unificado de escrita de transacoes passa a ter teste de rotas removidas e de escopos publicos no OpenAPI gerado.

## Contexto

Uma serie de recorrencia ou parcelamento e projetada em memoria. Uma ocorrencia virtual nao tem linha propria, e uma ocorrencia materializada pode ser individualizada, efetivada ou convertida, enquanto outras ocorrencias da mesma serie continuam virtuais. Uma versao por registro nao basta, porque uma alteracao em outra parte da serie pode mudar a projecao, as sobreposicoes e a previa de uma ocorrencia que nao foi tocada.

Ao mesmo tempo, uma exclusao de ocorrencia virtual que nao deixasse registro reapareceria na projecao em consultas posteriores. Essa ausencia precisa sobreviver a reinicios e nao pode depender de estado em memoria.

## Decisao

Cada grupo de recorrencia guarda uma revisao compartilhada (`VER_REVISAO_SERIE`). Toda mutacao que possa afetar a serie, suas excecoes, supressoes, projecao, situacao, dados ou resolucoes de sobreposicao incrementa essa revisao na mesma transacao de banco, inclusive exclusoes de ocorrencias protegidas e alteracoes de transacao simples destacada da serie.

A versao de uma ocorrencia de serie e composta pela revisao do grupo e, quando houver, pela versao do registro materializado. A versao de uma transacao simples ou de uma transferencia continua sendo a versao persistida do agregado. Essas versoes sao opacas, geradas pelo servidor e comparadas somente depois de o grupo ter sido bloqueado de forma pessimista e reconsultado.

A exclusao explicita de uma ocorrencia de serie com escopo `SOMENTE_ESTA` remove a Transacao materializada, se existir, e grava a Supressao de recorrencia na mesma transacao de banco. Essa supressao e duravel: a ocorrencia nao reaparece em consultas, nem apos reinicio, enquanto a serie existir. O escopo `ESTA_E_AS_PROXIMAS` nao grava supressao na exclusao; ele encerra a projecao por corte, o que impede a ocorrencia de ser projetada.

> **Emenda (Parte 2 da spec `.scratch/simplificar-repeticao/spec.md`).** Este paragrafo substitui o comportamento anterior de `SOMENTE_ESTA`, que tirava a ocorrencia da serie e gravava supressao. A alteracao com `SOMENTE_ESTA` nao grava supressao. A ocorrencia virtual vira uma Transacao vinculada ao segmento, com a data original como identidade e `excecaoRecorrencia = true`; esse registro ocupa a identidade segmento + data original na projecao e substitui a ocorrencia virtual, sem reaparecer como virtual. Uma ocorrencia ja fisica e alterada no proprio registro. A alteracao nao converte a ocorrencia: a repeticao enviada deve ser a lida, e conversoes para transacao simples ou nova regra ficam com `ESTA_E_AS_PROXIMAS`.

A exclusao exige `versaoEdicao` como parametro de consulta, para qualquer transacao simples, ocorrencia de serie ou transferencia. A versao e comparada depois do bloqueio (grupo, quando houver, e registro) e antes de qualquer gravacao; divergencia responde `409 VERSAO_DESATUALIZADA` sem alterar dados.

Decisao complementar: a conversao de uma ocorrencia de serie para transacao simples com `ESTA_E_AS_PROXIMAS` grava a Supressao da data original da ocorrencia selecionada, na mesma transacao de banco, antes de encerrar a projecao. Assim a ocorrencia original nao reaparece como virtual, independentemente do estado do segmento cortado (inclusive quando o corte coincide com o inicio e o segmento e cancelado). A exclusao com o mesmo escopo nao grava Supressao.

> **Emenda (simplificacao de `ESTA_E_AS_PROXIMAS`).** A resolucao de sobreposicoes deixa de existir: a alteracao com `ESTA_E_AS_PROXIMAS` nao aceita mais `politicaSobreposicao` nem `resolucoesSobreposicao`, e a previa de edicao foi removida. A regra passa a ser fixa: a alteracao e bloqueada com `422 OCORRENCIA_EFETIVADA_APOS_CORTE`, sem gravacao, quando a ocorrencia selecionada ou qualquer ocorrencia posterior do grupo estiver efetivada ou destacada como transacao simples. Caso contrario, as excecoes planejadas a partir do corte sao removidas antes da nova projecao e nenhuma candidata e descartada, portanto nenhuma candidata recebe Supressao. A supressao da conversao para transacao simples permanece. A revisao do grupo continua incrementada por toda escrita de serie. As mencoes a sobreposicoes e a previa no contexto e nas consequencias acima sao historicas.

Uma transacao simples destacada de uma serie tem versao composta pela revisao do grupo de origem e pela versao do registro, como uma ocorrencia de serie. Assim, uma escrita na serie torna desatualizada a versao lida antes da destacada.

Operacoes que afetem dois grupos existentes devem bloquear ambos em ordem deterministica de UUID. Nesta entrega nao ha operacao com essa forma; a regra fica registrada para quando houver.

## Alternativas consideradas

- Versao global por usuario: descartada porque faria uma alteracao em qualquer serie conflitar com escritas em series nao relacionadas.
- Versao somente por registro materializado: descartada porque ocorrencias virtuais nao possuem registro e mudancas na serie afetariam sua projecao sem alterar a versao.
- Exclusao virtual apenas em memoria ou por flag na consulta: descartada porque a ocorrencia reapareceria apos reinicio ou em outra consulta.
- Exclusao sem comparacao de versao: descartada. A exclusao exige `versaoEdicao` e a compara sob bloqueio, como as demais escritas.

## Consequencias

- Escritas de uma mesma serie sao serializadas pelo bloqueio do grupo; a concorrencia entre usuarios continua isolada, porque o bloqueio e restrito ao grupo do proprio usuario.
- Consultas de previa usam visao consistente, mas nunca autorizam gravacao sem nova comparacao de versao no PUT.
- A supressao e persistida em `TB008_SUPRESSAO_RECORRENCIA`, com unicidade por segmento e data original; a regra de reaparecimento depende dessa linha, e nao de estado de aplicacao.
- A exclusao de ocorrencia de serie compara a versao vigente sob o bloqueio do grupo; uma alteracao feita entre a leitura e a exclusao responde 409 e nao e removida.
- Uma perda de corrida de versao no banco (`OptimisticLockException`) e mapeada para `409 VERSAO_DESATUALIZADA`, nunca para erro interno.
