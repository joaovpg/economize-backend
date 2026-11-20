# 07: Contrato estrito para criar Parcelamento

**What to build:** a spec diz que Parcelamento "não aceita listas de vários dias semanais ou mensais". Criar um Parcelamento com campos de repetição que não se aplicam a ele (`diasSemana`, `diasMes`, `quantidadeOcorrencias`, `ate`) passa a ser rejeitado, em vez de ter os campos descartados em silêncio. Os aliases JSON não documentados na criação de Recorrência (`valorPorParcela`, `dataInicio`, `dataPrimeiraOcorrencia`, `count`, `dataFim`) são removidos, e o contrato fica igual ao OpenAPI.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Parcelamento com qualquer dos campos não aplicáveis responde `422` em RFC 9457, com código estável e indicação do campo.
- [ ] Recorrência comum mantém a aceitação desses campos conforme as regras atuais.
- [ ] Requisições com os aliases removidos não são mais interpretadas como os campos canônicos.
- [ ] O OpenAPI descreve quais campos valem para cada tipo de Grupo de recorrência.
- [ ] Testes HTTP cobrem cada campo rejeitado e a ausência dos aliases.
- [ ] `mvnw verify -B` passa.
