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

A exclusao explicita de uma ocorrencia de serie com escopo `SOMENTE_ESTA` remove a Transacao materializada, se existir, e grava a Supressao de recorrencia na mesma transacao de banco. Essa supressao e duravel: a ocorrencia nao reaparece em consultas, nem apos reinicio, enquanto a serie existir. O escopo `ESTA_E_AS_PROXIMAS` nao grava supressao; ele encerra a projecao por corte, o que impede a ocorrencia de ser projetada.

Operacoes que afetem dois grupos existentes devem bloquear ambos em ordem deterministica de UUID. Nesta entrega nao ha operacao com essa forma; a regra fica registrada para quando houver.

## Alternativas consideradas

- Versao global por usuario: descartada porque faria uma alteracao em qualquer serie conflitar com escritas em series nao relacionadas.
- Versao somente por registro materializado: descartada porque ocorrencias virtuais nao possuem registro e mudancas na serie afetariam sua projecao sem alterar a versao.
- Exclusao virtual apenas em memoria ou por flag na consulta: descartada porque a ocorrencia reapareceria apos reinicio ou em outra consulta.
- Versao obrigatoria na exclusao: adiada. A exclusao de serie usa o bloqueio do grupo, mas nao compara versao; isso fica registrado como pendencia no modelo financeiro.

## Consequencias

- Escritas de uma mesma serie sao serializadas pelo bloqueio do grupo; a concorrencia entre usuarios continua isolada, porque o bloqueio e restrito ao grupo do proprio usuario.
- Consultas de previa usam visao consistente, mas nunca autorizam gravacao sem nova comparacao de versao no PUT.
- A supressao e persistida em `TB008_SUPRESSAO_RECORRENCIA`, com unicidade por segmento e data original; a regra de reaparecimento depende dessa linha, e nao de estado de aplicacao.
- A exclusao de ocorrencia de serie nao compara versao, e uma alteracao feita entre a leitura e a exclusao pode ser removida. Esse limite e conhecido e fica registrado no modelo financeiro.
