# Especificação: documentação OpenAPI para integração do frontend

Status: aprovada para implementação

## Problema

O contrato OpenAPI atual expõe as rotas, mas deixa pouco claro o significado dos campos, os retornos condicionais e as regras das operações mais complexas. Isso aumenta o custo de integração do frontend, especialmente em consultas de transações e no gerenciamento de recorrências e parcelamentos.

## Decisões

- Documentar todas as 20 operações públicas; as operações de transações, transferências e recorrências receberão descrições de regras e retornos condicionais mais detalhadas.
- Preservar o JSON existente nesta etapa. A documentação explicará nulabilidade e significado por contexto, sem remodelar o contrato.
- Usar `operationId` e tags estáveis em português, alinhados ao vocabulário do domínio.
- Criar uma interface de contrato para cada Resource. As anotações HTTP e OpenAPI ficam na interface; a classe concreta implementa a interface e concentra a adaptação HTTP e a chamada do caso de uso.
- Anotar os DTOs HTTP com `@Schema`, incluindo descrição, formato, exemplos, limites e semântica condicional quando aplicável.
- Manter o filtro OpenAPI para preocupações transversais, como segurança por cookie, CSRF, schemas e respostas de erro comuns. Regras e respostas específicas ficam na operação.
- Documentar erros comuns e códigos de negócio relevantes nas respostas das operações, sem alterar o payload de erro existente.
- Ajustar a interface do Swagger UI para exibir `operationId`, iniciar as operações recolhidas e ordenar o contrato de forma previsível.

## Escopo de implementação

- `EconomizeApp`: metadados globais e descrições das tags.
- Interfaces `*ResourceApi`: caminhos, métodos HTTP, parâmetros, corpos, respostas, exemplos e regras de negócio.
- DTOs HTTP e enums de domínio expostos: descrições semânticas e exemplos.
- `application.yaml`: opções de legibilidade do Swagger UI.
- ADR registrando a decisão de separar contrato OpenAPI e implementação.

## Critérios de aceite

- Cada operação possui `operationId`, tag, resumo e descrição úteis para o frontend.
- A consulta de transações explica saldo de abertura, itens sintéticos, valor assinado e campos condicionais por `origem`.
- Recorrências e parcelamentos explicam escopo de edição, datas, frequência, regras de geração e respostas de operação.
- O JSON gerado mantém os nomes e formatos usados atualmente pela API.
- O OpenAPI gerado não depende de edição manual em `target/`.
- A compilação e a geração do OpenAPI passam; a verificação completa deve ser repetida quando o Docker estiver disponível para os testes de integração.
