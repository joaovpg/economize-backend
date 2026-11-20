# 03: Alterar a quantidade de parcelas respeita Segmentos posteriores ativos

**What to build:** alterar a quantidade total de um Parcelamento a partir de uma Parcela passa a respeitar Segmentos de recorrência posteriores ativos, com a mesma regra que a edição `THIS_AND_FUTURE` já aplica. Nenhum Segmento posterior pode continuar projetando Parcelas depois que o Grupo de recorrência original é encerrado.

**Blocked by:** 01 — Separar o gerenciamento de ocorrências recorrentes em casos de uso por intenção

**Status:** ready-for-agent

- [ ] Alterar a quantidade em uma Parcela anterior a um Segmento posterior ativo segue a mesma resposta (`422` e código) que `THIS_AND_FUTURE` usa nessa situação.
- [ ] Após uma alteração de quantidade bem-sucedida, a consulta de Transações não exibe Parcelas projetadas por Segmentos do Grupo antigo.
- [ ] Testes HTTP cobrem a alteração com e sem Segmento posterior ativo.
- [ ] `mvnw verify -B` passa.
