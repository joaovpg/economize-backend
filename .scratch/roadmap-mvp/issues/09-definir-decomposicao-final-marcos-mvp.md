# Definir a decomposição final dos marcos do MVP

Parent: [Reestruturar o roadmap do MVP por dependencias](../map.md)
Type: grilling
Status: resolved
Blocked by: 07, 08

## Question

Como `docs/roadmap.md` deve decompor e ordenar os marcos restantes do MVP, explicitar suas dependencias e gates, e definir criterios globais de prontidao suficientes para orientar especificacoes, tickets e agentes de implementacao sem antecipar contratos detalhados?

## Answer

O roadmap deve separar uma secao curta de `Base implementada`, sem apresenta-la como trabalho futuro. Ela registra a persistencia inicial do nucleo financeiro, o Cadastro de usuario, o login, a gestao de Categorias e a criacao atualmente disponivel de receitas e despesas planejadas. Deve tambem alertar que o schema inicial e o fluxo existente de criacao de Transacoes ainda precisam das adequacoes determinadas pelos marcos seguintes.

Os seis marcos restantes do MVP devem seguir esta ordem principal e estas dependencias:

1. Gestao de Contas financeiras depende da base implementada e forma, com Categorias, o gate para novas operacoes financeiras.
2. Gestao de Transacoes simples depende de Contas financeiras e adequa a criacao existente ao ciclo completo decidido para receitas e despesas sem repeticao.
3. Consultas de Transacoes e calculo de saldos depende de Contas financeiras e Transacoes simples.
4. Transferencias simples depende de Consultas de Transacoes e saldos, alem dos marcos anteriores.
5. Motor de recorrencia depende de Transacoes simples e Consultas de Transacoes e saldos, mas nao depende de Transferencias simples; os dois marcos podem avancar em paralelo depois do terceiro.
6. Parcelamentos depende do Motor de recorrencia e reutiliza sua infraestrutura sem confundir Parcelamento com Recorrencia.

A numeracao apresenta um caminho principal legivel, mas as dependencias explicitas, e nao a posicao no documento, determinam o que pode avancar em paralelo. Cada marco deve repetir as subsecoes `Objetivo`, `Depende de`, `Escopo`, `Regras criticas e dados` e `Concluido quando`. O roadmap deve resumir as decisoes consolidadas nos tickets anteriores, incluindo entidades e dados afetados, sem antecipar contratos HTTP campo a campo, cenarios exaustivos ou a decomposicao em trabalho executavel.

As correcoes em `docs/modelo-financeiro.md`, `CONTEXT.md` e `README.md`, assim como migrations incrementais, mapeamentos JPA e testes de schema, nao formam um marco isolado de remodelagem. Cada adequacao deve integrar o primeiro marco que necessitar da estrutura ou regra correspondente. A migration compartilhada `V1__criar_nucleo_financeiro.sql` permanece imutavel.

O MVP esta globalmente concluido somente quando os seis marcos estiverem concluidos; a documentacao refletir o comportamento entregue; as migrations incrementais, constraints e mapeamentos forem validados contra PostgreSQL; os recursos forem isolados pelo Usuario autenticado; os contratos HTTP e invariantes criticas tiverem testes de integracao; e `mvnw verify -B` for aprovado. Criterios de produto e cenarios mais detalhados pertencem as especificacoes de cada marco.

Depois do gate global do MVP, o roadmap deve apresentar a prioridade sugerida Orcamentos, Cartao de credito, Relatorios e Analises financeiras assistidas por IA, permitindo antecipacao quando as dependencias registradas estiverem satisfeitas. Conversao entre moedas, divisao por Categorias e compartilhamento de dados permanecem capacidades independentes sem prioridade fechada.
