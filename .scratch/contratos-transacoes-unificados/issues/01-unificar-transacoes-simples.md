# 01: Unificar cadastro e edição de transações simples

**What to build:** Permitir cadastrar e substituir receitas e despesas simples pelo contrato unificado, recebendo uma representação completa utilizável para edição e protegida contra alterações concorrentes.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

**Referências na spec** (`spec.md`):
- Contratos HTTP: `POST /api/transacoes` e `PUT /api/transacoes/{chaveEdicao}`
- Princípios comuns
- Identidade, versão e concorrência
- Estados de edição e respostas
- Erros e segurança

**Contrato essencial:**
- `POST /api/transacoes` recebe `tipo`, `contaId`, `categoriaId` (anulável), `descricao`, `observacoes` (anulável), `valor`, `dataFinanceira`, `situacao` e `repeticao` (anulável). Nesta issue, somente `repeticao: null` é aceito.
- `PUT /api/transacoes/{chaveEdicao}` recebe todos os campos do POST e `versaoEdicao`. Não recebe `escopo` (transação simples).
- `201 Created` no POST, com `Location`, `chaveEdicao`, `versaoEdicao` e o item completo do GET, com valor assinado. `200 OK` no PUT.
- A versão de transação simples é a versão persistida do agregado, protegida por versão otimista ou bloqueio equivalente.
- Campos desconhecidos, ausentes ou malformados retornam `400 DADOS_INVALIDOS`, com `errors` quando possível. Strings vazias não substituem `null`.
- Enquanto a issue 03 não estiver pronta, `repeticao` não nulo continua retornando `422 REPETICAO_INVALIDA`.

**Estado observado no código** (em 2026-11-08):
- `chaveEdicao` e `versaoEdicao` já aparecem em `TransacaoResourceApi`, `AlterarTransacao` e `ConsultarTransacoes`. `IdentidadeEdicaoTransacao.java` é um arquivo novo na branch.
- `CriarTransacao.validar` rejeita qualquer `repeticao` não nulo (`CriarTransacao.java:84-86`).
- Os critérios abaixo estão marcados, mas o status é `ready-for-agent`. Confirmar cada item contra os testes antes de considerar a issue concluída, porque a issue 03 depende dela.

**Critérios de aceite:**
- [x] POST aceita os campos completos, incluindo repetição nula, e devolve 201, Location, impacto assinado, chave e versão; PUT exige representação completa e devolve a identidade e versão vigentes.
- [x] A chave opaca é segura para URL e identifica o recurso; chave inválida, inexistente ou de outro usuário resulta em 404 indistinguível, e versão obsoleta resulta em 409 sem dados financeiros.
- [x] Valores, datas, situação, propriedade, moeda, saldo inicial e manutenção de associações inativas seguem as regras da especificação; efetivar, corrigir e replanejar preservam ou limpam o instante corretamente.
- [x] Campos desconhecidos, ausentes ou malformados são rejeitados conforme o contrato; escopo e resolução não aplicáveis são rejeitados.
- [x] Testes HTTP com PostgreSQL verificam criação, edição, isolamento, concorrência e rollback, e a leitura fornece os dados necessários para editar transações simples.

**Fora de escopo:** repetição e parcelamento (issue 03), conversões (issues 04 a 06), transferências (issue 07).

**Como verificar:**
- `.\mvnw.cmd -Dtest=TransacaoResourceTest test`, incluindo testes de concorrência e rollback com PostgreSQL real.
- Antes de concluir: `.\mvnw.cmd verify -B`.
