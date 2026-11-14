# Modelo financeiro

O MVP registra e cataloga movimentacoes financeiras pessoais. Cada registro financeiro pertence a um unico usuario.

## Entidades

| Tabela | Entidade | Responsabilidade |
| --- | --- | --- |
| `TB001_USUARIO` | Usuario | Identidade, credencial e isolamento dos dados |
| `TB002_CONTA_FINANCEIRA` | Conta financeira | Nome, moeda, saldo inicial e disponibilidade operacional |
| `TB003_CATEGORIA` | Categoria | Classificacao hierarquica opcional de receitas e despesas |
| `TB004_GRUPO_RECORRENCIA` | Grupo de recorrencia | Rastreabilidade da origem comum de segmentos e excecoes, e a revisao compartilhada da serie |
| `TB005_SEGMENTO_RECORRENCIA` | Segmento de recorrencia | Trecho independente governado por uma RRULE e pela politica de datas |
| `TB006_TRANSACAO` | Transacao | Receita, despesa, ocorrencia recorrente ou lado de transferencia |
| `TB007_TRANSFERENCIA` | Transferencia | Operacao logica que vincula uma saida e uma entrada |
| `TB008_SUPRESSAO_RECORRENCIA` | Supressao de recorrencia | Ausencia intencional de uma ocorrencia virtual |

## Convencoes fisicas

Tabelas usam `TB<numero>_<NOME>`. Colunas usam os prefixos `ID_`, `STR_`, `DEC_`, `INT_`, `BOL_`, `DAT_`, `DHR_` e `VER_`. Indices usam `IX<tabela>_<sequencia>_<finalidade>`, e constraints usam `PK`, `FK`, `UK` ou `CK` seguidos do numero da tabela.

Os numeros de tabela sao permanentes e nao devem ser reutilizados. Identificadores SQL e Java permanecem em portugues sem acentos. Termos da RFC 5545, como RRULE e DTSTART, mantem a nomenclatura oficial.

## Saldos e transacoes

O saldo financeiro nao e armazenado. Ele e derivado do saldo inicial da conta e de todas as Transacoes desde `DAT_SALDO_INICIAL`. Valores persistidos de Transacoes sao sempre positivos; o tipo determina se cada registro entra ou sai da conta. A Situacao de uma Transacao e `PLANEJADA` ou `EFETIVADA` e serve somente ao controle operacional: ambas produzem o mesmo impacto nos saldos derivados. Uma Transacao planejada nao possui instante de efetivacao. Uma Transacao efetivada exige Data financeira igual ou anterior a data atual no fuso do Usuario e registra separadamente em `DHR_EFETIVACAO` o instante em que foi efetivada.

A consulta de Transacoes exige os meses inicial e final no formato `yyyy-MM`. O intervalo e inclusivo, admite de um a doze meses civis e pode filtrar por Contas financeiras e Categorias exatas. Nao existe filtro por Situacao: Transacoes planejadas e efetivadas sempre aparecem e participam das somas. Identificadores de filtros inexistentes ou alheios sao tratados como recurso nao encontrado.

A resposta retorna a linha do tempo e o Saldo de abertura consolidado em `BRL`. Cada item informa sua origem e um valor de impacto assinado, positivo para entradas e negativo para saidas; o contrato nao expoe o tipo persistido. O Saldo de abertura usa como corte o ultimo dia anterior ao primeiro mes consultado e soma os saldos iniciais ja vigentes com todos os movimentos anteriores atendidos pelos filtros. O filtro de Categoria seleciona os movimentos sem remover a base formada pelos saldos iniciais das Contas financeiras selecionadas e exclui Transferencias, que nao possuem Categoria.

Quando a data do saldo inicial de uma Conta financeira cai dentro do intervalo, um Item de saldo inicial e inserido nessa data antes das Transacoes do mesmo dia. O item usa a Conta financeira como identificador da operacao, preserva o sinal do saldo inicial e e omitido quando esse saldo e zero. O frontend parte do Saldo de abertura e soma os valores dos itens em ordem para derivar os saldos diarios. Esses itens nunca sao persistidos.

Receitas e despesas sao criadas, editadas e excluidas pelo contrato de Transacoes com representacao completa. `repeticao` nulo cria uma Transacao simples; com repeticao ou parcelamento, a primeira ocorrencia entra em uma serie (ver Recorrencia). A leitura e as respostas de escrita retornam impacto assinado, uma chave de edicao e uma versao de edicao. A chave identifica a Transacao, mas nao autoriza seu acesso; a versao e comparada sob bloqueio na mesma transacao da alteracao, e uma versao desatualizada produz conflito sem expor dados financeiros. Campos anulaveis de escrita devem ser enviados explicitamente, inclusive quando nulos. A exclusao de uma Transacao simples usa a mesma chave, exige `versaoEdicao` e nao recebe escopo.

Receitas e despesas simples podem substituir atomicamente Situacao, Conta financeira, Categoria, tipo, descricao, observacoes, valor e Data financeira. Efetivar uma planejada registra um novo instante; corrigir uma efetivada preserva esse instante; replanejar o limpa. Uma Conta financeira ou Categoria inativada depois da associacao pode ser mantida, mas uma nova associacao exige recurso ativo do mesmo Usuario. A movimentacao para outra Conta financeira tambem exige a mesma moeda e Data financeira compativel com o saldo inicial.

Contas podem ser ativadas e inativadas repetidamente sem perder historico. Categorias tambem podem ser ativadas e inativadas e classificam receitas e despesas simples. Sua hierarquia admite profundidade arbitraria; os casos de uso de categorias detectam ciclos indiretos e verificam que pai e filha pertencem ao mesmo usuario. Transferencias nao possuem Categoria.

## Contas financeiras

Uma Conta financeira pertence a um Usuario e possui nome, moeda, saldo inicial, data do saldo inicial e situacao. O nome e armazenado sem espacos nas extremidades e e unico por Usuario sem diferenciar caixa, inclusive entre contas inativas. O MVP aceita somente `BRL` e representa o saldo inicial em `NUMERIC(19,4)`, permitindo valores positivos, negativos ou zero.

No contrato HTTP de Contas, a situacao e representada pelo campo booleano `ativo`: `true` significa disponivel para novas associacoes e `false` significa indisponivel. O filtro opcional da listagem usa o mesmo campo.

Moeda, saldo inicial e data do saldo inicial formam os Dados iniciais da conta. Eles ficam irreversivelmente bloqueados no primeiro vinculo com uma operacao financeira persistida ou com uma fonte de operacoes virtuais. Transacoes e lados de Transferencias acionam o bloqueio por meio de `TB006_TRANSACAO`; Segmentos de recorrencia e futuros Parcelamentos o acionam pela estrutura que projeta suas ocorrencias. Excluir posteriormente essas operacoes nao libera os Dados iniciais.

A data de uma Transacao ou o inicio de um Segmento nao pode anteceder a data do saldo inicial. Novas associacoes exigem Conta financeira ativa; a inativacao nao altera nem impede a manutencao das operacoes que ja estavam associadas.

Categorias sao cadastradas ativas. Nome e unico sem diferenciar caixa entre categorias irmas, inclusive entre categorias raiz e independentemente da situacao. Uma categoria so pode ser cadastrada ou movida para um pai ativo. A ativacao exige todos os ancestrais ativos, enquanto a inativacao exige que nao existam descendentes ativos. Edicoes podem alterar dados, posicao e situacao atomicamente e nunca podem formar ciclos.

No contrato HTTP de Categorias, a Situacao e representada pelo campo booleano `ativo`: `true` significa ativa e `false` significa inativa. O filtro opcional da listagem tambem usa `ativo`, sem alterar o conceito de Situacao no dominio.

## Recorrencia

Um grupo preserva a origem comum e seu tipo, `RECORRENCIA` ou `PARCELAMENTO`. Cada segmento possui dados financeiros proprios, DTSTART, periodo opcional e uma RRULE canonica sem o prefixo `RRULE:`. O motor aceita `DAILY`, `WEEKLY`, `MONTHLY` e `YEARLY`, alem de `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY` e `BYMONTHDAY` no subconjunto definido pela aplicacao.

A politica de datas fica em cada Segmento (`STR_POLITICA_DATA`), para que um segmento preserve sua politica mesmo quando a modalidade muda. Os segmentos existentes receberam a politica pelo tipo do grupo na migration V8: Recorrencias usam `PADRAO`; Parcelamentos usam `AJUSTAR_ULTIMO_DIA_MES` para reproduzir datas mensais e anuais inexistentes no cliente.

A consulta unificada devolve a configuracao estruturada da repeticao (frequencia, intervalo, dias, politica, termino e parcelamento), traduzida da RRULE sem recalcular datas, inclusive para inicio fora do primeiro dia da semana ou do mes e para mensal sem dias. Cada grupo guarda `VER_REVISAO_SERIE`, a revisao compartilhada que compoe as versoes de edicao das ocorrencias da serie.

Ocorrencias virtuais sao expandidas em memoria e nao geram Transacoes por efeito colateral. A identidade logica e `Segmento de recorrencia + Data original da ocorrencia`. Uma ocorrencia de serie recebe a chave de edicao `1:r:` com esses dois valores; a mesma identidade permanece ao efetivar, individualizar ou alterar a ocorrencia, e somente a conversao para transacao simples ou para nova serie a substitui. A resposta sempre devolve a chave vigente.

### Escopos

Os unicos escopos publicos sao `SOMENTE_ESTA` e `ESTA_E_AS_PROXIMAS`. Eles valem para alteracao com `PUT /api/transacoes/{chaveEdicao}` e para exclusao com `DELETE /api/transacoes/{chaveEdicao}?escopo=`; a exclusao, em qualquer caso, exige `versaoEdicao`. Efetivacao e replanejamento sao alteracoes de situacao feitas pelo PUT.

- `SOMENTE_ESTA` altera ou exclui apenas a ocorrencia selecionada. Na exclusao, remove a Transacao materializada, se existir, e grava a Supressao de recorrencia na mesma transacao de banco, de forma duravel, para que a ocorrencia nao reapareca em consultas posteriores.
- `ESTA_E_AS_PROXIMAS` altera ou exclui a ocorrencia selecionada e as seguintes. O corte usa a data original: o segmento cortado termina na vespera, ou fica cancelado quando o corte coincide com o seu inicio, e os segmentos ativos posteriores do grupo sao cancelados. Materializacoes planejadas a partir do corte sao removidas; individualizadas e efetivadas posteriores sao preservadas. A ocorrencia selecionada, se materializada, e removida. A exclusao nao grava Supressao, porque o corte ja impede a projecao. O grupo fica `CANCELADO` quando o corte elimina toda a projecao virtual e `CONCLUIDO` quando preserva ocorrencias virtuais anteriores para consulta.

Excluir uma ocorrencia de serie exige o escopo; sem ele, ou com qualquer outro valor, a operacao e recusada. Transacao simples nao recebe escopo.

### Conversoes

- Transacao simples para repeticao ou parcelamento: a propria transacao vira a primeira ocorrencia da nova serie, sem copia.
- Ocorrencia de serie com `SOMENTE_ESTA` para transacao simples: a ocorrencia vira simples e deixa de pertencer ao grupo; a ocorrencia original fica suprimida e a serie continua. A leitura mantem `serie` como historico, com `repeticao` nulo e `escoposDisponiveis` vazio.
- Ocorrencia de serie com `SOMENTE_ESTA` para nova repeticao ou parcelamento: a ocorrencia inicia um novo grupo; a original fica suprimida e a serie anterior continua.
- Ocorrencia de serie com `ESTA_E_AS_PROXIMAS` para simples, repeticao ou parcelamento: encerra a projecao antiga a partir da data original, mantem as protegidas posteriores e inicia o novo trecho ou a transacao simples com a ocorrencia selecionada.
- Parcelamento para parcelamento: a numeracao e preservada quando o total nao muda; mudar o total encerra o plano anterior e cria novo grupo a partir da selecionada.
- Repeticao para parcelamento exige numeracao da primeira parcela e total. Parcelamento para repeticao exige termino; a numeracao anterior permanece no historico.

## Identidade, versao e concorrencia

`chaveEdicao` e uma string opaca e URL-safe. Uma transacao simples usa a identidade da propria transacao; uma ocorrencia de serie usa segmento e data original. A chave nao revela dados financeiros e nao e autorizacao. Uma ocorrencia destacada como transacao simples mantem a identidade da transacao persistida e preserva os metadados de origem separadamente.

`versaoEdicao` e uma string opaca gerada pelo servidor. Para transacao simples e transferencia, corresponde a versao persistida do agregado. Para uma ocorrencia de serie, combina a revisao compartilhada do grupo e a versao do registro materializado, quando existir; uma ocorrencia virtual muda de versao quando o grupo muda.

A revisao do grupo (`VER_REVISAO_SERIE`) e incrementada por qualquer mutacao que possa afetar a serie: alteracao ou exclusao de ocorrencia, conversao, supressao, mudanca da projecao, da situacao ou dos dados e resolucao de sobreposicoes. As escritas de serie bloqueiam o grupo de forma pessimista, reconsultam e comparam a versao antes de gravar e gravam tudo na mesma transacao de banco. Uma perda de corrida ou versao obsoleta responde `409 VERSAO_DESATUALIZADA`, sem dados financeiros e sem gravacao parcial.

## Previa e sobreposicoes

`POST /api/transacoes/{chaveEdicao}/previa-edicao` calcula, sem gravar nada e sem alterar saldos, as ocorrencias que a alteracao produziria em um periodo de um a doze meses civis e as sobreposicoes com ocorrencias protegidas posteriores ao corte. A protegida e uma ocorrencia individualizada ou efetivada. Uma sobreposicao existe quando a Data financeira de uma nova ocorrencia coincide com a de uma protegida posterior ao corte; a propria ocorrencia selecionada nao concorre consigo mesma, e a data original, sozinha, nao caracteriza sobreposicao. A previa nao gera token.

O PUT recebe a alteracao com `versaoEdicao` e a politica geral, e recebe escolhas especificas por data original candidata. Escolhas especificas prevalecem sobre a politica geral. `MANTER_SOMENTE_PRESERVADA` descarta a candidata e mantem a protegida; `MANTER_AMBAS` mantem os dois impactos. A politica geral aceita `MANTER_SOMENTE_PRESERVADA` na previa, e `MANTER_AMBAS` tambem no PUT. A protegida nunca e alterada pela resolucao, e a candidata descartada fica suprimida de forma duravel, sem reaparecer. Escolhas duplicadas, desconhecidas ou que nao correspondem a uma candidata sobreposta respondem `422 RESOLUCAO_SOBREPOSICAO_INVALIDA`. O PUT revalida versao, propriedade, conflitos e escolhas na mesma transacao; uma previa desatualizada responde `409`.

## Transferencias

Uma Transferencia possui duas Transacoes sem Categoria: uma despesa na Conta financeira de origem e uma receita na Conta financeira de destino. Situacao, Data financeira, valor, descricao, observacoes e instante de efetivacao pertencem a operacao logica, permanecem iguais nos tres registros e sao alterados atomicamente. Os lados vinculados nao podem ser editados ou excluidos isoladamente.

No MVP, as contas pertencem ao mesmo Usuario, sao diferentes e usam a mesma moeda. A criacao e uma nova associacao exigem Conta financeira ativa; manter uma associacao existente continua permitido depois da inativacao. `PLANEJADA` e `EFETIVADA` sao as unicas Situacoes. Efetivar exige Data financeira igual ou anterior a data atual no fuso do Usuario, registra um unico instante nos tres registros e replanejar o limpa.

Transferencias sao criadas, substituidas ou excluidas por contratos proprios de escrita, sempre com valor nominal positivo. Sua leitura ocorre somente na consulta unificada de Transacoes. Cada lado usa o identificador da Transferencia como identificador da operacao, informa origem `TRANSFERENCIA`, Conta contraparte, Categoria nula e impacto financeiro proprio: negativo na origem e positivo no destino. Quando ambos participam do calculo, seus valores se anulam.

## Limites desta entrega

Esta entrega fornece migrations, entidades, repositorios, constraints estruturais, cadastro de usuario, autenticacao JWT, gestao de categorias, gestao de Contas financeiras, o ciclo completo de receitas e despesas simples, a consulta unificada com Saldo de abertura, o motor de recorrencias e parcelamentos com seus contratos unificados de escrita, e a exclusao de transacoes, ocorrencias e transferencias.

As escritas de series usam `POST /api/transacoes` com `repeticao`, `PUT /api/transacoes/{chaveEdicao}` com `escopo` e `DELETE /api/transacoes/{chaveEdicao}?escopo=`. As rotas antigas `/api/recorrencias` e os escopos `ONLY_THIS` e `THIS_AND_FUTURE` foram removidos, sem alias.

Pendencias conhecidas:

- A nova serie iniciada por `ESTA_E_AS_PROXIMAS` nao guarda vinculo explicito com a ocorrencia cortada. O historico permanece no segmento encerrado do mesmo grupo, quando a modalidade nao muda, e nas protegidas posteriores. Registrar esse vinculo exige decisao de modelo e migration, porque a constraint `CK006_07` impede origem em registro de serie.
- O total atual do parcelamento nao e calculado nem exposto, conforme a especificacao.
- Nao ha, nesta entrega, operacao que afete dois grupos existentes. A ordem deterministica de bloqueio por UUID fica como regra para operacoes futuras desse tipo.
