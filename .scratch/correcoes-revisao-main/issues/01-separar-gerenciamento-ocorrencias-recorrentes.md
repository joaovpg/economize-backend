# 01: Separar o gerenciamento de ocorrências recorrentes em casos de uso por intenção

**What to build:** prefatoração sem mudança de comportamento observável. O caso de uso único que hoje edita, efetiva e exclui ocorrências e altera a quantidade de parcelas de um Parcelamento passa a ser dividido em casos de uso nomeados pela intenção (por exemplo `EditarOcorrenciaRecorrente`, `EfetivarOcorrenciaRecorrente`, `ExcluirOcorrenciaRecorrente` e `AlterarQuantidadeParcelamento`). As regras compartilhadas passam a ter um único lugar: encerrar um Segmento de recorrência, criar o novo Segmento após um corte, filtrar ocorrências efetivadas futuras, criar supressões e derivar a política de dia do mês do tipo de Grupo de recorrência. Isso deixa as correções dos tickets 02 a 06 pequenas e localizadas.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Cada operação pública de ocorrência recorrente é atendida por um caso de uso nomeado pela intenção, sem sufixo `Service`.
- [ ] O encerramento de Segmento, a montagem do novo Segmento, o filtro de efetivadas futuras e a criação de supressão existem uma única vez cada.
- [ ] A regra "Parcelamento ajusta para o último dia do mês" é decidida em um único ponto, compartilhado pela gestão e pela resolução de ocorrências.
- [ ] Ramos condicionais com corpo idêntico e leituras/regravações sem efeito são removidos.
- [ ] Contrato HTTP e OpenAPI gerado permanecem idênticos.
- [ ] A suíte existente passa sem alteração de testes; `mvnw verify -B` passa.
