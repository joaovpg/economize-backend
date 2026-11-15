# ADR 0015: Repeticao ancorada na data financeira

## Status

Aceita.

Substitui a configuracao estruturada de repeticao da spec de contratos unificados (`diasSemana`, `diasMes`, `politicaData` e `parcelamento` na entrada). Nao altera a ADR 0009. Altera a ADR 0014 somente na edicao `SOMENTE_ESTA` (Parte 2 da spec `simplificar-repeticao`), registrada como emenda naquela ADR; esta ADR trata apenas da configuracao de repeticao (Parte 1).

## Contexto

O contrato de repeticao expunha quase toda a RRULE ao cliente. Isso exigia cerca de quinze regras de combinacao entre frequencia, dias, politica de data, termino e parcelamento, e permitia combinacoes contraditorias. O uso real do produto e mais restrito: repetir a cada X dias, semanas, meses ou anos; terminar nunca, numa data, apos N ocorrencias ou ao fim de um Parcelamento; e, no mensal, repetir no N-esimo dia util.

A politica `PADRAO` tambem produzia um resultado pouco natural para financas pessoais: uma serie mensal do dia 31 pulava os meses sem dia 31.

## Decisao

- A Data financeira da primeira ocorrencia e sempre a ancora da serie. O cliente nao informa dias da semana nem dias do mes.
- `repeticao` passa a ter `frequencia`, `intervalo`, `ancoragem` (`DIA_DO_MES` ou `DIA_UTIL`) e `termino`.
- Mensal e anual com `DIA_DO_MES` sempre ajustam para o ultimo dia do mes quando a data nao existe. A politica de data deixa de ser escolha do cliente e passa a ser derivada pelo servidor.
- O Parcelamento passa a ser o tipo de termino `PARCELAS` (`numeroPrimeiraParcela`, `totalParcelas`), em vez de um objeto paralelo mutuamente exclusivo com o termino.
- `DIA_UTIL` vale somente para `MENSAL`. N e calculado pelo servidor a partir da Data financeira e limitado a 20. Nesta entrega, dia util e de segunda a sexta, representado por `BYDAY=MO,TU,WE,TH,FR;BYSETPOS=n` e expandido pelo iCal4j atras do port `MotorRecorrencia`.

## Alternativas consideradas

- **Manter a RRULE estruturada:** descartada pela quantidade de regras de combinacao sem caso de uso correspondente.
- **Cliente informar N do dia util:** descartada. Exigiria validar que a data informada e o N-esimo dia util. Derivar N da data elimina essa regra e combina com a interface, que mostra uma opcao para a data escolhida.
- **Considerar feriados nacionais no dia util:** adiada. A RRULE nao expressa feriados, e a regra exigiria um calendario compartilhado com o frontend e um passo extra apos a expansao. Alteracoes no calendario tambem mudariam datas ja projetadas.
- **`diaUtil` como inteiro anulavel:** descartado em favor do enum `ancoragem`, que permite acrescentar "ultimo dia util" sem codificar um valor especial como `-1`.

## Consequencias

- A validacao de repeticao fica reduzida a limites de intervalo, regras do dia util e regras de cada tipo de termino.
- Repeticoes com varios dias por semana ou por mes deixam de ser expressaveis e precisam ser cadastradas como series separadas.
- Num mes com feriado antes do N-esimo dia util, a ocorrencia projetada sai um dia util antes da data real. O Usuario corrige a ocorrencia com `SOMENTE_ESTA`.
- Segmentos existentes sao normalizados para a forma canonica por migration, sem mudar nenhuma data. Se existir algum segmento que mudaria de datas ou nao tem representacao, a migration falha e nada e alterado (especificacao `simplificar-repeticao`, "Segmentos existentes").
- Criacao e edicao usam a mesma validacao, porque a ancora e sempre a primeira ocorrencia do trecho.
