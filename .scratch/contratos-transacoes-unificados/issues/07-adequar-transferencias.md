# 07: Adequar cadastro e edição de transferências

**What to build:** Permitir cadastrar e editar transferências com o contrato completo e controle de versão, preservando os dois lados e sua leitura unificada.

**Blocked by:** None (can start immediately).

**Status:** done

**Referências na spec** (`spec.md`):
- Contratos HTTP: `POST /api/transferencias` e `PUT /api/transferencias/{transferenciaId}`
- `GET /api/transacoes` (leitura dos dois lados)
- Estados de edição e respostas (campos de transferência)
- Erros e segurança

**Contrato essencial:**
- `POST /api/transferencias` recebe exatamente `contaOrigemId`, `contaDestinoId`, `descricao`, `observacoes` (anulável), `valor` (positivo), `dataFinanceira` e `situacao`.
- `PUT /api/transferencias/{transferenciaId}` recebe os mesmos campos e `versaoEdicao`.
- Campos fora dessa lista, como `categoriaId`, `repeticao` e `parcelamento`, são desconhecidos e retornam `400 DADOS_INVALIDOS`.
- `POST` retorna `201` com `Location`. `PUT` retorna `200`. Ambos devolvem `transferenciaId`, `versaoEdicao`, `contaOrigemId`, `contaDestinoId`, `descricao`, `observacoes`, `valor` nominal positivo, `dataFinanceira`, `situacao` e `efetivadoEm`.
- Contas: pertencem ao usuário, são diferentes e têm a mesma moeda. Nova associação exige conta ativa. Associação já existente em conta inativa pode ser mantida.
- Saldo inicial, data e situação são validados. Efetivação, correção e replanejamento mantêm `efetivadoEm` igual nos três registros (operação e dois lados).
- Leitura: cada lado aparece com a mesma `operacaoId` e versão, a conta contraparte e impacto oposto. Filtro de categoria exclui transferências. IDs internos dos lados não são expostos.
- Erros: versão obsoleta, `409 VERSAO_DESATUALIZADA`; recurso alheio ou inexistente, `404` indistinguível.

**Estado observado no código** (em 2026-11-08):
- A V5 já implementou transferências simples. Antes de planejar a mudança, verificar o que o código já faz, por exemplo a rejeição de campos desconhecidos e a versão de edição.

**Critérios de aceite:**
- [ ] POST e PUT aceitam apenas os campos especificados e devolvem representação completa, valor nominal positivo, versão e instante; criação retorna 201 e Location, edição retorna 200.
- [ ] Contas pertencem ao usuário, são diferentes e usam mesma moeda; novas associações exigem conta ativa e associações existentes inativas podem ser mantidas.
- [ ] Saldo inicial, data e situação são validados; efetivação, correção e replanejamento mantêm dados comuns e instante iguais nos três registros.
- [ ] Os dois lados aparecem na leitura com mesma operação e versão, contrapartes e impactos opostos; filtro de categoria os exclui e IDs internos dos lados não são expostos.
- [ ] Versão obsoleta retorna 409, recursos alheios são indistinguíveis de inexistentes e falhas não deixam alterações parciais; testes HTTP cobrem concorrência e atomicidade.

**Fora de escopo:** transferências recorrentes ou parceladas e conversões entre transferência e transação.

**Como verificar:**
- Testes HTTP com PostgreSQL real: criação, edição, contas de terceiros e inativas, concorrência e atomicidade.

## Comments

- Implementado sobre a V5 existente (transferência já tinha `@Version`, bloqueio pessimista e rejeição de campos desconhecidos via `fail-on-unknown-properties`). Acrescentado: `versaoEdicao` na escrita e na resposta (PUT exige; versão obsoleta retorna 409 `VERSAO_DESATUALIZADA` após o bloqueio); `transferenciaId` no lugar de `id`; `Location` absoluto no POST (201); `observacoes` obrigatória no PUT (chave presente, anulável), opcional no POST, conforme a spec.
- Mapeamento de `DADOS_INVALIDOS` para `/transferencias` (campos desconhecidos e ausentes) estendido nos mappers de `transacao/http` e `shared/http/JsonProblemPostProcessor`. Os mappers ainda estão no pacote de transação; movê-los para `shared` fica fora deste ticket.
- Flush antes de montar o resultado do PUT, para devolver a versão pós-atualização.
- Migration `V9__adiar_verificacao_vinculos_lados_transferencia.sql`: as FKs compostas `FK007_04`/`FK007_05` passam a `DEFERRABLE INITIALLY DEFERRED`. Sem isso, trocar a conta de um lado falha, porque a FK exige a conta anterior até o commit. Coberta por `TransferenciaVinculosMigrationTest` e pelo teste HTTP de troca de conta.
- Testes HTTP em `TransferenciaResourceTest`: contrato completo e Location, campos fora do contrato e ausentes, versão obsoleta, contas de terceiros/inativas/data do saldo, efetivação/correção/replanejamento com mesmo instante nos dois lados, leitura sem IDs internos, falha sem alteração parcial, concorrência com mesma versão (200/409).
- Ajuste em `TransacaoResourceTest` (helper `criarTransferencia`): lê `transferenciaId`.
- Não atendido: a regra de moedas diferentes (`MOEDAS_TRANSFERENCIA_DIFERENTES`) não é alcançável pela API, porque `ContaValidation.moeda` aceita apenas `BRL`. Não há teste HTTP para ela; a proteção existe na aplicação e no trigger `FN007_VALIDAR_TRANSFERENCIA`.
