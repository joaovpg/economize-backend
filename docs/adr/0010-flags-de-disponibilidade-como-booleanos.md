# ADR 0010: Flags de disponibilidade como booleanos

## Status

Aceita.

## Contexto

Contas financeiras ja armazenam a disponibilidade em `BOL_ATIVO`, mas o contrato HTTP e parte do caso de uso ainda usam `SituacaoConta` com `ATIVA` e `INATIVA`. Usuarios usam `StatusUsuario` com `ATIVO` e `BLOQUEADO` em uma coluna textual `STR_STATUS`. Essa duplicacao faz a aplicacao comparar um valor semantico booleano com um literal de enum e cria uma linguagem diferente da usada por Categorias.

Transacoes e Transferencias tambem possuem duas situacoes, mas `PLANEJADA` e `EFETIVADA` governam o instante de efetivacao e nao significam disponibilidade. Recorrencias possuem tres estados de ciclo de vida. Esses estados nao fazem parte desta decisao.

## Decisao

Flags de disponibilidade serao representados por `ativo` no Java e no contrato HTTP, e por `BOL_ATIVO BOOLEAN NOT NULL` no PostgreSQL.

- `ContaFinanceira` conserva a coluna `BOL_ATIVO` existente e deixa de expor `SituacaoConta`/`situacao`.
- `Usuario` migra de `StatusUsuario`/`STR_STATUS` para `boolean ativo`/`BOL_ATIVO`.
- A migration converte `ATIVO` para `true` e `BLOQUEADO` para `false` antes de remover a coluna textual.
- Cadastros criam registros ativos sem receber um campo de disponibilidade.
- A API de Contas usa `ativo` na resposta, na edicao e no filtro de listagem; nao existe alias para `situacao`.
- Situacoes operacionais de Transacoes, Transferencias e Recorrencias permanecem enums e colunas textuais.

## Alternativas consideradas

- **Manter enums e textos:** rejeitada porque duplica uma informacao booleana e mantem comparacoes como `ATIVO == ATIVO`.
- **Manter ambos temporariamente:** rejeitada porque criaria duas fontes de verdade e prolongaria a nomenclatura legada no contrato.
- **Converter todos os estados para booleanos:** rejeitada porque confundiria disponibilidade com efetivacao e ciclo de vida de Recorrencias.
- **Usar `BIT(1)`:** rejeitada porque `BOOLEAN` e o tipo nativo do PostgreSQL e ja e usado no schema.

## Consequencias

- O modelo de disponibilidade fica consistente entre Usuario, Conta financeira e Categoria.
- Consultas e validacoes podem usar predicados booleanos como `ativo = true`.
- A alteracao do contrato de Contas e incompatível com clientes que enviam `situacao`.
- A migration e irreversivel no sentido pratico para dados ja convertidos; os valores antigos sao preservados apenas pelo mapeamento `ATIVO`/`BLOQUEADO` para `true`/`false` durante a aplicacao.
- Estados operacionais continuam dispondo de nomes explicitos e de suas regras atuais.
