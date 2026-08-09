# Flags de disponibilidade como booleanos

## Objetivo

Remover os enums que apenas espelham a disponibilidade de um recurso e usar o campo booleano `ativo` de forma consistente na aplicacao, no contrato HTTP e no banco de dados.

## Escopo aprovado

Inclui:

- `ContaFinanceira`, que ja possui `ativo` e `BOL_ATIVO`, mas ainda expoe `SituacaoConta` e `situacao` no contrato;
- `Usuario`, que hoje usa `StatusUsuario` e `STR_STATUS`;
- migration incremental dos dados existentes;
- testes de migration, persistencia, autenticacao e contrato HTTP de Contas;
- documentacao da decisao e do contrato.

Nao inclui:

- `SituacaoTransacao` ou `SituacaoTransferencia` (`PLANEJADA`/`EFETIVADA`);
- `StatusRecorrencia` (`ATIVO`/`CONCLUIDO`/`CANCELADO`);
- `TipoTransacao`, `OrigemItemConsulta` ou enums de recorrencia que nao representam disponibilidade;
- compatibilidade temporaria com o nome `situacao`.

## Decisoes

### Modelo de dominio e persistencia

Disponibilidade e representada por `ativo` na entidade e por `BOL_ATIVO BOOLEAN NOT NULL` no PostgreSQL.

- `ContaFinanceira.ativo` e `TB002_CONTA_FINANCEIRA.BOL_ATIVO` permanecem como estao.
- `Categoria` nao sofre alteracao estrutural, pois ja usa `ativo` no Java, no contrato e no banco.
- `Usuario` troca `StatusUsuario status` por `boolean ativo`, mapeado para `TB001_USUARIO.BOL_ATIVO`.
- `StatusUsuario` e `SituacaoConta` sao removidos.
- Entidades e casos de uso usam predicados booleanos: `isAtivo()` e `ativo = true`.
- Cadastros continuam criando Contas e Usuarios ativos; `ativo` nao e aceito nos requests de cadastro.

### Contrato HTTP de Contas

O contrato passa a ser:

- `POST /api/contas`: nao recebe `ativo` e cria a Conta ativa;
- `GET /api/contas`: lista todas as Contas do Usuario;
- `GET /api/contas?ativo=true|false`: filtra pela disponibilidade;
- `PUT /api/contas/{contaId}`: recebe `ativo` booleano obrigatorio;
- respostas de Conta retornam `ativo: true|false`.

O campo `situacao`, os valores `ATIVA`/`INATIVA` e o enum `SituacaoConta` deixam de fazer parte do contrato. Requests antigos nao terao alias ou fallback.

Requests usam `Boolean` com `@NotNull` para diferenciar campo ausente de `false`; respostas e resultados internos usam `boolean`.

### Migration

`V1__criar_nucleo_financeiro.sql` nao sera alterada. A migration `V7__representar_disponibilidade_como_booleano.sql` deve:

1. adicionar `TB001_USUARIO.BOL_ATIVO` sem `NOT NULL` inicialmente;
2. converter `STR_STATUS = 'ATIVO'` para `true` e `STR_STATUS = 'BLOQUEADO'` para `false`;
3. aplicar `DEFAULT TRUE` e `NOT NULL`;
4. remover `CK001_01_STATUS`;
5. remover `STR_STATUS`.

A conversao ocorre antes da remocao da coluna antiga. `BOOLEAN` e preferido a `BIT(1)` por ser o tipo nativo do PostgreSQL e ja ser usado nas tabelas de Conta e Categoria.

### Fluxo de aplicacao

O Resource de Contas extrai `Boolean ativo` da query string e encaminha o valor ao caso de uso. O mapper traduz requests e resultados sem regras. `ListarContas` e `ContaFinanceiraRepository` aceitam o filtro opcional `Boolean ativo`; sem filtro, listam todas as Contas do Usuario.

`UsuarioRepository.buscarAtivo` consulta `ativo = true`. Cadastro e autenticacao deixam de importar `StatusUsuario`; a autenticacao continua recusando Usuarios com `ativo = false`.

## Verificacao

### Migration

O teste de migration deve iniciar a partir do schema legado, inserir Usuarios `ATIVO` e `BLOQUEADO`, aplicar as migrations e verificar que:

- `BOL_ATIVO` existe e e `BOOLEAN`;
- `STR_STATUS` nao existe;
- `ATIVO` foi convertido para `true`;
- `BLOQUEADO` foi convertido para `false`.

Os testes que preparam dados no schema anterior podem continuar usando `STR_STATUS` apenas para exercitar a migracao historica.

### Aplicacao e HTTP

Os testes devem comprovar:

- cadastro de Conta retorna `ativo: true`;
- edicao aceita `ativo: false` e retorna `ativo: false`;
- edicao aceita reativacao com `ativo: true`;
- listagem filtra por `ativo=true` e `ativo=false`;
- request de edicao sem `ativo` e rejeitado;
- Usuario inativo nao autentica nem e aceito pelos casos de uso que exigem Usuario ativo;
- fixtures Java nao dependem dos enums removidos.

## Consequencias

O contrato fica alinhado com Categorias e com a modelagem fisica existente. A API perde a nomenclatura redundante `situacao` para disponibilidade, mas a mudanca e incompatível com consumidores que ainda enviam `situacao`. Estados operacionais continuam expressivos e nao ficam comprimidos em booleanos ambiguos.

## Criterios de aceite

- Nenhum codigo de producao importa `SituacaoConta` ou `StatusUsuario`.
- O schema atual possui `BOL_ATIVO` em Usuario, Conta financeira e Categoria, e nao possui `TB001_USUARIO.STR_STATUS`.
- O contrato de Contas usa `ativo` em requests, respostas e filtro.
- `V1` permanece inalterada e a migration incremental passa nos testes de migration.
- `SituacaoTransacao`, `SituacaoTransferencia` e `StatusRecorrencia` permanecem sem alteracao semantica.
