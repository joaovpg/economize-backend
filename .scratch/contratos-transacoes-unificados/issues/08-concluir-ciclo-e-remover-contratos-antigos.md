# 08: Concluir o ciclo de vida e retirar contratos antigos

**What to build:** Concluir a substituição dos contratos de escrita, preservando a exclusão e tornando a API e sua documentação coerentes com os novos fluxos.

**Blocked by:** 04: Converter transação simples em repetição ou parcelamento; 06: Alterar esta ocorrência e as próximas com prévia de sobreposições; 07: Adequar cadastro e edição de transferências.

**Status:** ready-for-agent

**Referências na spec** (`spec.md`):
- Compatibilidade e migração (últimos dois parágrafos: remoção de rotas antigas e exclusão)
- Códigos de erro adicionais
- Objetivo e contexto (atualização de documentação de domínio e ADRs)
- Verificação e critérios de aceite
- Fora de escopo

**Contrato essencial:**
- A exclusão de transação, ocorrência e transferência continua disponível, com revisão de série, proteção e supressão conforme a spec. Não remover uma rota de exclusão sem contrato equivalente, e atualizar OpenAPI e testes.
- Remover ou substituir as rotas antigas de escrita de recorrência e os valores públicos em inglês de escopo. Efetivação e replanejamento ficam cobertos pelo PUT.
- OpenAPI: campos condicionais, autenticação, erros RFC 9457 e códigos finais, conforme a seção "Códigos de erro adicionais".
- Documentação: atualizar `CONTEXT.md`, `docs/modelo-financeiro.md` e as ADRs pertinentes com as decisões implementadas. Registrar uma nova ADR para decisões arquiteturais relevantes, como a spec pede.
- Logs: não registrar chaves, corpos de requisição nem dados financeiros.

**Estado observado no código** (em 2026-11-08):
- A spec não lista quais são as rotas antigas nem os valores ingleses de escopo. Levantar isso no código antes de remover (por exemplo em `recorrencia/http/RecorrenciaResourceApi` e nos resources de transação) e anotar a lista nesta issue.

**Critérios de aceite:**
- [ ] Exclusão de transações, ocorrências e transferências continua acessível por contrato equivalente, com revisão de série, proteção, supressão e isolamento conforme a especificação.
- [ ] Escritas antigas de recorrência e valores públicos ingleses de escopo são removidos ou substituídos; efetivação e replanejamento continuam cobertos pelo PUT.
- [ ] OpenAPI descreve contratos, campos condicionais, autenticação, erros RFC 9457 e códigos finais; documentação de domínio e ADRs pertinentes refletem as decisões implementadas.
- [ ] Testes integrados cobrem fluxos e conversões completos, leitura, filtros, saldos, migração, versões, rollback e isolamento entre usuários; o contrato não registra chaves ou dados financeiros em logs.
- [ ] A verificação completa do projeto passa com o comando de CI exigido para Windows.

**Como verificar:**
- `.\mvnw.cmd verify -B` (único gate de CI, conforme `AGENTS.md`).
