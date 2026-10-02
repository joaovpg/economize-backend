# Contexto do Economize

## Glossario

- **Usuario**: identidade que possui credencial e e proprietaria dos dados financeiros.
- **Cadastro de usuario**: criacao autonoma de um Usuario ativo com inicio imediato de uma sessao autenticada.
- **Conta financeira**: local ao qual transacoes pertencem e que define moeda e saldo inicial.
- **Situacao da conta financeira**: disponibilidade operacional de uma Conta financeira, representada por `ATIVA` ou `INATIVA`.
- **Dados iniciais da conta**: moeda, saldo inicial e data do saldo inicial definidos no cadastro de uma Conta financeira.
- **Categoria**: classificacao hierarquica opcional de receitas e despesas.
- **Situacao da categoria**: disponibilidade de uma Categoria, representada por `ATIVA` ou `INATIVA`.
- **Transacao**: registro financeiro de receita, despesa ou lado interno de uma transferencia.
- **Transacao simples**: receita ou despesa sem vinculo com Transferencia, Grupo de recorrencia, Segmento de recorrencia ou Parcelamento, mantida diretamente pelo modulo de Transacoes.
- **Situacao da transacao**: controle operacional de uma Transacao simples, representado no MVP por `PLANEJADA` ou `EFETIVADA`; ambas as situacoes produzem o mesmo impacto nos saldos derivados.
- **Data financeira**: data civil que posiciona uma Transacao na linha do tempo financeira, independentemente do instante em que ela foi efetivada.
- **Instante de efetivacao**: instante em que uma Transacao passou para `EFETIVADA`; e preservado em correcoes enquanto ela permanece efetivada e limpo ao replanejar.
- **Saldo de abertura**: saldo consolidado no dia anterior ao primeiro mes consultado, derivado dos saldos iniciais ja vigentes e de todas as Transacoes anteriores atendidas pelos filtros de Conta financeira e Categoria.
- **Item de saldo inicial**: entrada da linha do tempo que representa o inicio de uma Conta financeira com saldo inicial diferente de zero dentro do periodo consultado; nao e uma Transacao.
- **Origem do item**: agregado que governa o ciclo de vida de um item da linha do tempo, como `TRANSACAO_SIMPLES`, `TRANSFERENCIA` ou `SALDO_INICIAL_CONTA`.
- **Impacto financeiro**: valor assinado de um item da linha do tempo; entradas sao positivas e saidas sao negativas, enquanto valores persistidos e contratos de escrita permanecem positivos.
- **Transferencia**: operacao atomica que vincula uma despesa na Conta financeira de origem e uma receita na Conta financeira de destino.
- **Situacao da transferencia**: condicao compartilhada pela Transferencia e suas duas Transacoes, representada no MVP por `PLANEJADA` ou `EFETIVADA`.
- **Conta contraparte**: a outra Conta financeira de uma Transferencia em relacao ao lado exibido na linha do tempo.
- **Grupo de recorrencia**: origem comum preservada para segmentos e excecoes recorrentes.
- **Segmento de recorrencia**: trecho independente de uma recorrencia governado por uma RRULE.
- **Ocorrencia recorrente**: instancia financeira prevista por um Segmento de recorrencia, virtual enquanto apenas projetada e persistida quando efetivada ou individualizada.
- **Excecao recorrente**: ocorrencia persistida ou supressao que deixa de seguir os dados do Segmento sem perder a identidade de sua origem.
- **Tipo de grupo**: classificacao do plano como `RECORRENCIA` ou `PARCELAMENTO`; os dois usam segmentos, mas somente o segundo possui numeracao de parcelas.
- **Data original da ocorrencia**: data produzida pela RRULE que identifica uma ocorrencia junto com o Segmento, mesmo quando a Data financeira de uma excecao e alterada.
- **Supressao de recorrencia**: registro da ausencia intencional de uma ocorrencia virtual; nao e uma Transacao. A exclusao explicita de uma Transacao materializada pode criar a Supressao atomicamente para impedir seu reaparecimento.
- **Escopo de edicao**: alcance da alteracao de uma ocorrencia, `ONLY_THIS` ou `THIS_AND_FUTURE`; o segundo so vale para ocorrencia virtual.
- **Parcelamento**: plano financeiro finito composto por parcelas numeradas, com valor por parcela e quantidade total original definidos na criacao.
- **Parcela**: ocorrencia numerada de um Parcelamento, virtual enquanto apenas projetada e persistida quando efetivada ou individualizada.
- **Numero da parcela**: posicao atual de uma Parcela dentro do Segmento de recorrencia que a originou; e exposto como `numeroParcela` e fica nulo para Recorrencias comuns.
- **Total de parcelas**: quantidade total usada para exibir a numeracao de um Parcelamento no Segmento de recorrencia atual; e exposta como `totalParcelas` e fica nula para Recorrencias comuns.
- **Total contratado original**: valor por parcela multiplicado pela quantidade total original de um Parcelamento, derivado sob demanda a partir dos dados preservados e nao persistido nesta entrega.
- **Total atual do parcelamento**: soma derivada das parcelas existentes; nao e calculada nem exposta pelo contrato atual.

## Convencoes de linguagem

- Use `Transacao`, e nao `Lancamento`, para o registro financeiro persistido em `TB006_TRANSACAO`.
- Use nomes de casos de uso orientados a intencao, como `CriarTransacao` e `EfetivarTransacao`.
- `Service` nao nomeia um conceito do dominio e nao deve substituir uma intencao especifica.
- Use `CadastrarCategoria`, `EditarCategoria` e `ListarCategorias` para as intencoes de gestao de categorias.
- Use `CadastrarConta`, `EditarConta` e `ListarContas` para as intencoes de gestao de Contas financeiras.
