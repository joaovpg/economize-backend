# 08: Concluir o ciclo de vida e retirar contratos antigos

**What to build:** Concluir a substituição dos contratos de escrita, preservando a exclusão e tornando a API e sua documentação coerentes com os novos fluxos.

**Blocked by:** 04: Converter transação simples em repetição ou parcelamento; 06: Alterar esta ocorrência e as próximas com prévia de sobreposições; 07: Adequar cadastro e edição de transferências.

**Status:** done

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

## Comments

Concluída. `.\mvnw.cmd verify -B` passa com 276 testes (após `spotless:apply`, que só reformatou arquivos Java).

**Rotas antigas removidas** (levantadas em `recorrencia/http/RecorrenciaResourceApi` e `TransacaoResourceApi`):
- `POST /api/recorrencias` (`criarRecorrencia`, com `tipoGrupo`): substituído por `POST /api/transacoes` com `repeticao` (ticket 03).
- `PUT /api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}` (`alterarOcorrenciaRecorrente`): substituído por `PUT /api/transacoes/{chaveEdicao}` com `escopo` (tickets 05 e 06).
- `POST /api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}/efetivar` (`efetivarOcorrenciaRecorrente`): efetivação passa pelo PUT com `situacao: EFETIVADA`. Com `SOMENTE_ESTA` e `repeticao: null`, a ocorrência de série vira transação simples (matriz de conversão da spec).
- `DELETE /api/recorrencias/{segmentoId}/ocorrencias/{dataOriginal}?escopo=` (`excluirOcorrenciaRecorrente`): substituído por `DELETE /api/transacoes/{chaveEdicao}?escopo=`, com contrato de exclusão abaixo.
- `DELETE /api/transacoes/{id}` (UUID, somente transação simples): mesma rota e verbo, com a chave `1:s:` no lugar do UUID. O UUID responde 404.

**Valores de escopo em inglês removidos** (`EscopoOcorrencia`): `ONLY_THIS` (substituído por `SOMENTE_ESTA`) e `THIS_AND_FUTURE` (substituído por `ESTA_E_AS_PROXIMAS`). Não há alias: enviar um desses valores responde 422 `ESCOPO_INVALIDO`.

Junto com a rota antiga, deixaram de ser públicos os valores de frequência em inglês `DAILY`, `WEEKLY`, `MONTHLY` e `YEARLY` (campo `frequencia` do corpo antigo). O enum `FrequenciaRecorrencia` continua interno, para a RRULE; a leitura usa `DIARIA`, `SEMANAL`, `MENSAL` e `ANUAL`.

**Contrato de exclusão** (`DELETE /api/transacoes/{chaveEdicao}?escopo=`):
- Chave simples (`1:s:`): 204. `escopo` presente responde 422 `ESCOPO_INVALIDO`.
- Chave de série (`1:r:`): `escopo` é obrigatório. Ausente ou fora de `SOMENTE_ESTA`/`ESTA_E_AS_PROXIMAS` responde 422 `ESCOPO_INVALIDO`.
- `SOMENTE_ESTA` remove a transação materializada, se houver, grava a Supressão na mesma transação de banco e revisa o grupo.
- `ESTA_E_AS_PROXIMAS` agora também aceita ocorrência materializada, como o PUT. Antes só valia para virtual. Remove a selecionada, encerra o segmento na data original, cancela os segmentos posteriores, remove as planejadas e preserva as protegidas posteriores. Revisa o grupo.
- Chave inválida, inexistente, desativada ou alheia responde 404 `RECURSO_NAO_ENCONTRADO`.

**Código removido:** `recorrencia/http/**` (resource, API, mapper e DTOs), `CriarRecorrencia`, `CriarParcelamento`, `GerenciarOcorrenciaRecorrente` (a exclusão passa a `transacao/application/ExcluirOcorrenciaSerie`) e `EscopoOcorrencia`. Sem migration.

**Testes:**
- `RecorrenciaResourceTest` foi removido. Os comportamentos foram cobertos pelos testes do novo contrato: criação e consulta em `ConsultaSeriesResourceTest` e `CriarRepeticaoTransacaoResourceTest`; edição, individualização, efetivação e replanejamento em `AlterarOcorrenciaSerieResourceTest` e `AlterarEstaEAsProximasResourceTest`; alteração de total em `AlterarEstaEAsProximasResourceTest`; exclusões (virtual, efetivada, corte, vários trechos, numeração) em `ExcluirTransacaoResourceTest`.
- `ExcluirTransacaoResourceTest` (novo): contrato de exclusão, escopos, supressão, revisão e isolamento.
- `ContratoEscritaUnificadaResourceTest` (novo): rotas antigas respondem 404 ou 405, escopos em inglês são recusados, e o OpenAPI gerado não publica rotas nem escopos antigos e publica o DELETE com o enum `EscopoEdicaoTransacao`.
- Atualizados: `TransacaoResourceTest` e `AlterarOcorrenciaSerieResourceTest` (exclusão pela chave). No helper de efetivação de `AlterarEstaEAsProximasResourceTest`, o PUT substitui a rota antiga, e a origem esperada da efetivada individualizada passou a `TRANSACAO_SIMPLES`.
- Os testes novos foram escritos antes da implementação, mas a primeira execução foi depois dela. Não houve fase vermelha observada.

**Documentação:** `CONTEXT.md` (escopos, chave de edição, versão de edição, revisão da série, ocorrência protegida, previa, sobreposição, conversão); `docs/modelo-financeiro.md` (escopos, identidade e versão, previa e sobreposições, conversões, pendências); `docs/roadmap.md` (marcos 5 e 6 marcados como concluídos, com os nomes de escopo e as rotas atuais); nova ADR `docs/adr/0014-revisao-compartilhada-serie-e-supressao-duravel.md`. A ADR 0012 não foi alterada; a 0014 registra que o teste OpenAPI passou a existir. Pendências: a chave de série da issue 04 foi resolvida na issue 05 e está documentada como comportamento atual. O vínculo histórico da nova série (issue 05) continua pendente e registrado no modelo financeiro.

**Logs:** conferido. `TraceIdFilter` registra método, template de rota, status e duração. `HttpErrorLoggingInterceptor` registra mensagens fixas. Nenhuma chave, corpo ou query é registrada. Nada precisou ser corrigido.

**Critérios não atendidos ou parciais:**
- A exclusão de série não recebe `versaoEdicao`. A spec não define o contrato de exclusão. A exclusão usa o bloqueio do grupo e revisa a série, mas não compara versão. Registrado em `modelo-financeiro.md` e na ADR 0014. A exclusão de transação simples também não usa versão, como antes.
- Bloqueio de dois grupos por ordem de UUID: nenhuma operação atual afeta dois grupos existentes, então a regra não foi implementada.
- OpenAPI: revisados os contratos de exclusão, alteração e prévia (descrições, respostas 400, 404, 409 e 422, parâmetros, autenticação e `application/problem+json`). O restante do documento não foi revisado campo a campo.
