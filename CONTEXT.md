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
- **Supressao de recorrencia**: registro da ausencia intencional de uma ocorrencia virtual; nao e uma Transacao. A exclusao de uma ocorrencia de serie com escopo `SOMENTE_ESTA` grava a Supressao na mesma transacao de banco para impedir seu reaparecimento; na exclusao, `ESTA_E_AS_PROXIMAS` encerra a projecao por corte e nao grava Supressao. Na alteracao, a conversao da ocorrencia selecionada para transacao simples com `ESTA_E_AS_PROXIMAS` grava a Supressao da data original, e cada candidata descartada por `MANTER_SOMENTE_PRESERVADA` em novo trecho tambem e suprimida (ver ADR 0014).
- **Escopo de edicao**: alcance de uma alteracao ou exclusao de ocorrencia de serie, `SOMENTE_ESTA` ou `ESTA_E_AS_PROXIMAS`. O segundo encerra a projecao a partir da data original do corte e preserva as ocorrencias protegidas posteriores.
- **Ocorrencia protegida**: ocorrencia de serie individualizada ou efetivada. Alteracoes em lote com `ESTA_E_AS_PROXIMAS` nao as alteram nem as removem; somente a ocorrencia selecionada pode ser alterada ou excluida explicitamente.
- **Chave de edicao**: identificador opaco e URL-safe de uma Transacao simples ou de uma ocorrencia de serie (segmento e data original). Nao autoriza acesso e nao revela dados financeiros; a ocorrencia destacada como transacao simples mantem a chave da propria Transacao.
- **Versao de edicao**: token opaco comparado sob bloqueio antes de cada escrita. Para Transacao simples e Transferencia, corresponde a versao persistida; para ocorrencia de serie, combina a revisao da serie e a versao do registro materializado, quando existir.
- **Revisao da serie**: contador compartilhado de um Grupo de recorrencia, incrementado por toda mutacao que possa afetar a serie, suas excecoes, supressoes, projecao, situacao, dados ou resolucoes de sobreposicao.
- **Previa de edicao**: calculo sem gravacao das ocorrencias que uma alteracao produziria em um periodo, com as sobreposicoes encontradas. A previa nao gera token; o PUT recalcula e revalida.
- **Sobreposicao**: conflito entre uma nova ocorrencia e uma ocorrencia protegida posterior ao corte que possuem a mesma Data financeira. Resolve-se por `MANTER_SOMENTE_PRESERVADA` (descarta a nova) ou `MANTER_AMBAS`, por escolha especifica da data original candidata ou pela politica geral.
- **Conversao de ocorrencia**: troca de uma ocorrencia de serie por Transacao simples ou por nova repeticao ou parcelamento, conforme o escopo; a origem permanece como historico e a serie continua, exceto quando o escopo encerra a projecao.
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
- Escopos publicos sao em portugues: `SOMENTE_ESTA` e `ESTA_E_AS_PROXIMAS`. Nao oferecer aliases em ingles dos escopos antigos (`ONLY_THIS`, `THIS_AND_FUTURE`).
