# Roadmap

## Base implementada

- Persistencia inicial do nucleo financeiro com PostgreSQL, Flyway, entidades JPA, repositories Panache e testes de integracao.
- Cadastro de Usuario com inicio imediato de sessao autenticada.
- Login por e-mail e senha com Argon2 e token JWT.
- Gestao de Categorias com cadastro, edicao, alteracao de situacao e listagem; na API HTTP, a situacao e representada por `ativo` booleano e o filtro da listagem usa o mesmo campo.
- Gestao de Contas financeiras com cadastro, edicao, alteracao de situacao e listagem.
- Gestao de Transacoes simples, com criacao planejada ou efetivada, alteracao, transicoes e exclusao definitiva.
- Transferencias simples atomicas, com escrita propria e lados incorporados a consulta unificada de Transacoes.

O schema inicial e o fluxo existente de criacao de Transacoes ainda precisam das adequacoes de ciclo de vida, integridade e contrato descritas nos marcos restantes. A migration inicial compartilhada permanece imutavel; mudancas de schema serao migrations incrementais introduzidas no primeiro marco que necessitar de cada regra.

## Marcos do MVP

A numeracao abaixo apresenta o caminho principal. Marcos concluidos permanecem registrados para preservar as dependencias e o escopo entregue.

### 1. Gestao de Contas financeiras

**Estado:** concluido.

#### Objetivo

Permitir que o Usuario mantenha as Contas financeiras usadas por Transacoes, saldos e Transferencias.

#### Depende de

- Base implementada.
- Gestao de Categorias, que juntamente com Contas financeiras forma o gate para novas operacoes financeiras.

#### Escopo

- Cadastrar Conta financeira.
- Listar Contas financeiras ativas e inativas, com filtro opcional por situacao e sem paginacao.
- Editar Conta financeira.
- Ativar e inativar Conta financeira.
- Nao inclui consulta individual, exclusao ou calculo de saldo.

#### Regras criticas e dados

- Toda Conta financeira pertence ao Usuario autenticado. Recursos inexistentes e recursos de outro Usuario sao tratados sem revelar sua existencia.
- O cadastro cria a Conta financeira ativa.
- O nome e obrigatorio, normalizado e unico por Usuario sem diferenciar caixa, inclusive entre contas inativas. A listagem usa ordenacao deterministica por nome sem diferenciar caixa.
- O MVP aceita somente `BRL`, preservando a moeda explicita no modelo e no contrato para evolucao futura.
- O saldo inicial aceita valores positivos, negativos ou zero com precisao monetaria `NUMERIC(19,4)`.
- A data do saldo inicial deve ser igual ou anterior a data atual no fuso do Usuario.
- Nome pode ser alterado a qualquer momento. Os Dados iniciais da conta somente podem ser alterados enquanto nunca tiver existido uma operacao financeira vinculada a conta.
- O primeiro vinculo com Transacao, lado de Transferencia, Segmento de recorrencia ou Parcelamento bloqueia irreversivelmente os Dados iniciais, mesmo que a operacao seja excluida depois.
- Nenhuma Transacao, lado de Transferencia, Segmento de recorrencia, ocorrencia materializada ou Parcela pode ter Data financeira anterior a data do saldo inicial da conta.
- Contas podem ser ativadas e inativadas repetidamente. A inativacao impede novas operacoes, mas nao altera operacoes existentes nem bloqueia sua consulta, correcao, efetivacao, replanejamento ou exclusao conforme o ciclo de vida aplicavel.
- Unicidade de nome, moeda, bloqueio dos Dados iniciais e datas compativeis sao protegidos pela aplicacao e pela migration incremental correspondente.

#### Concluido quando

- Cadastro, listagem, edicao e alteracao de situacao possuem contratos HTTP e casos de uso.
- Isolamento por Usuario e invariantes de nome, moeda, saldo inicial, data e historico estao protegidos na aplicacao e no banco quando aplicavel.
- Testes de integracao cobrem os fluxos e regras criticas.
- Documentacao e descricao do estado implementado refletem a entrega.

### 2. Gestao de Transacoes simples

**Estado:** concluido.

#### Objetivo

Completar o ciclo de vida de receitas e despesas sem repeticao, adequando o fluxo de criacao ja existente.

#### Depende de

- Gestao de Contas financeiras.
- Gestao de Categorias.

#### Escopo

- Criar Transacao simples como `PLANEJADA` ou `EFETIVADA`.
- Alterar Transacao simples.
- Excluir fisicamente Transacao simples planejada ou efetivada.
- Tratar somente receitas e despesas sem Grupo ou Segmento de recorrencia e sem vinculo com Transferencia.
- Consultas pertencem ao marco seguinte.

#### Regras criticas e dados

- `PLANEJADA` e `EFETIVADA` sao as unicas Situacoes da transacao no MVP. `CANCELADA` sera removida do enum e da constraint de banco.
- A alteracao pode mudar atomicamente situacao, Conta financeira, Categoria, tipo, descricao, observacoes, valor e Data financeira.
- Uma Transacao pode transitar nos dois sentidos entre planejada e efetivada.
- Efetivar exige uma data civil igual ou anterior a data atual. Essa data passa a ser a Data financeira, enquanto `efetivadoEm` registra o instante da efetivacao.
- Alterar a Data financeira de uma Transacao efetivada preserva o instante original. Replanejar limpa `efetivadoEm`; efetivar novamente registra outro instante.
- O tipo pode alternar entre `RECEITA` e `DESPESA`, nunca para `TRANSFERENCIA`.
- Mover a Transacao exige uma nova Conta financeira ativa, do mesmo Usuario, da mesma moeda e com data do saldo inicial compativel.
- Uma Categoria inativa ja associada pode ser mantida ou removida. Uma nova associacao exige Categoria ativa do mesmo Usuario.
- Uma Conta financeira inativa nao recebe novas Transacoes ou novas associacoes, mas operacoes existentes continuam corrigiveis, efetivaveis, replanejaveis e excluiveis.
- A exclusao e definitiva e nao oferece lixeira, restauracao ou versionamento financeiro. Alterar ou excluir uma efetivada muda naturalmente o saldo derivado.
- Transacoes vinculadas a Recorrencia, Parcelamento ou Transferencia nao podem ser alteradas ou excluidas pelo fluxo de Transacoes simples.

#### Concluido quando

- Criacao, alteracao e exclusao possuem contratos HTTP e casos de uso.
- O schema e os mapeamentos representam somente as situacoes aprovadas e mantem coerencia entre situacao e instante de efetivacao.
- Propriedade e invariantes de conta, Categoria, tipo, valor, Data financeira e situacao estao protegidas.
- Testes de integracao cobrem os fluxos, transicoes, exclusao fisica e isolamento por Usuario.
- Modelo financeiro, glossario e descricao do estado implementado refletem o ciclo entregue.

### 3. Consultas de Transacoes e Saldo de abertura

**Estado:** concluido.

#### Objetivo

Oferecer uma linha do tempo unificada e o Saldo de abertura necessario para o frontend derivar saldos diarios.

#### Depende de

- Gestao de Contas financeiras.
- Gestao de Transacoes simples.

#### Escopo

- Consultar Transacoes de um intervalo obrigatorio entre um e doze meses civis inclusivos.
- Receber os meses inicial e final no formato `yyyy-MM`.
- Filtrar por uma ou mais Contas financeiras e Categorias exatas.
- Unificar Transacoes simples, lados de Transferencias e ocorrencias recorrentes ou Parcelas quando esses modulos estiverem disponiveis.
- Calcular o Saldo de abertura consolidado em `BRL` no ultimo dia anterior ao primeiro mes consultado.
- Nao usar paginacao.

#### Regras criticas e dados

- Inicio e fim sao obrigatorios, inclusivos, usam granularidade mensal, devem estar em ordem e podem abranger no maximo doze meses.
- A listagem ordena por Data financeira crescente. Itens de saldo inicial precedem Transacoes do mesmo dia, e o identificador fornece o desempate deterministico.
- Sem filtros, a consulta inclui todas as Contas financeiras, Categorias e Situacoes do periodo. Contas inativas continuam consultaveis.
- Cada item identifica se sua origem e saldo inicial de Conta financeira, Transacao simples, Transferencia, Recorrencia ou Parcelamento e referencia a operacao relacionada.
- A Situacao da transacao e somente um controle operacional. Planejadas e efetivadas aparecem na linha do tempo e produzem o mesmo impacto nas somas.
- O Saldo de abertura soma os saldos iniciais ja vigentes e todas as Transacoes anteriores ao primeiro mes, respeitando os filtros de Conta financeira e Categoria.
- O filtro de Categoria seleciona movimentos, mas preserva os saldos iniciais das Contas financeiras selecionadas como base.
- Uma Conta financeira iniciada dentro do intervalo produz um item `SALDO_INICIAL_CONTA` nessa data, antes das Transacoes do mesmo dia. Saldos iniciais zero nao produzem itens.
- Consultar expande ocorrencias recorrentes e Parcelas virtuais em memoria, sem grava-las. Ocorrencias anteriores ao intervalo compoem o Saldo de abertura e ocorrencias dentro do intervalo aparecem na linha do tempo.
- Os lados de uma Transferencia afetam suas respectivas contas e se anulam no Saldo de abertura ou no saldo diario quando ambas participam do calculo.
- Saldo de abertura e saldos diarios nao sao persistidos.
- Indices por Usuario, Data financeira, Conta financeira e Categoria serao definidos conforme as queries reais.

#### Concluido quando

- Intervalo mensal, filtros e Saldo de abertura possuem contrato HTTP e caso de uso.
- Resultados persistidos e virtuais formam uma unica listagem deterministica, quando os modulos correspondentes estiverem disponiveis.
- Saldo de abertura e itens respeitam Data financeira, periodo e filtros sem distinguir Situacoes da transacao.
- Testes de integracao cobrem limites de periodo, filtros, ordenacao, contas inativas, Saldo de abertura e itens de saldo inicial.
- O glossario define Saldo de abertura e Item de saldo inicial.

### 4. Transferencias simples

**Estado:** concluido.

#### Objetivo

Permitir movimentacoes atomicas entre duas Contas financeiras do mesmo Usuario.

#### Depende de

- Gestao de Contas financeiras.
- Gestao de Transacoes simples.
- Consultas de Transacoes e Saldo de abertura.

#### Escopo

- Criar Transferencia como `PLANEJADA` ou `EFETIVADA`.
- Alterar, efetivar e replanejar Transferencia por uma unica substituicao completa.
- Excluir fisicamente Transferencia planejada ou efetivada.
- Exibir e consultar Transferencias somente pelos dois lados na consulta unificada de Transacoes.
- Nao inclui Transferencias recorrentes, parceladas, cancelamento ou estorno.

#### Regras criticas e dados

- Origem e destino pertencem ao Usuario autenticado, sao distintas e usam a mesma moeda.
- Criar uma Transferencia exige contas ativas. Operacoes existentes em contas inativas continuam editaveis, efetivaveis, replanejaveis e excluiveis; uma nova associacao exige conta ativa.
- A Transferencia cria uma despesa na origem e uma receita no destino na mesma transacao de banco. O tipo legado `TRANSFERENCIA` nao representa uma Transacao valida.
- Os dois lados nao possuem Categoria. Informar um filtro de Categoria na consulta unificada exclui Transferencias.
- Valor, Data financeira, descricao, observacoes, situacao e instante de efetivacao sao iguais entre a operacao e seus dois lados.
- Os lados permanecem vinculados e nunca podem ser alterados ou excluidos isoladamente.
- Alterar contas, valor, Data financeira, descricao, observacoes ou situacao atualiza atomicamente a Transferencia e seus lados.
- Efetivar atribui o mesmo `efetivadoEm` aos tres registros. Replanejar limpa os tres instantes; efetivar novamente gera outro instante comum.
- Uma Transferencia efetivada exige Data financeira igual ou anterior a data atual no fuso do Usuario.
- `PLANEJADA` e `EFETIVADA` sao as unicas Situacoes da transferencia. Estados de cancelamento e estorno e o relacionamento de estorno serao removidos do schema.
- A exclusao remove atomicamente a Transferencia e suas duas Transacoes, sem permitir lados orfaos.
- A integridade de Usuario, contas, moeda, tipos e dados compartilhados permanece protegida no banco com mecanismo compativel com operacoes existentes em contas inativas.
- Na consulta unificada, os dois lados usam origem `TRANSFERENCIA`, o identificador da Transferencia como identificador da operacao e informam a Conta contraparte. O valor e negativo na saida, positivo na entrada e a saida aparece primeiro.

#### Concluido quando

- Criacao, alteracao e exclusao possuem contratos HTTP e casos de uso; a leitura ocorre exclusivamente pela consulta unificada de Transacoes.
- Escritas e exclusoes dos tres registros sao atomicas.
- O schema representa somente o ciclo de vida aprovado e impede lados inconsistentes, orfaos ou manipulados isoladamente.
- A consulta unificada e os saldos incorporam os lados corretamente.
- Testes de integracao cobrem fluxos, invariantes, atomicidade e isolamento por Usuario.

### 5. Motor de recorrencia

**Estado:** em implementacao.

#### Objetivo

Validar, persistir e expandir o subconjunto aprovado de repeticao para receitas e despesas, mantendo ocorrencias futuras virtuais.

#### Depende de

- Gestao de Transacoes simples.
- Consultas de Transacoes e Saldo de abertura.
- Nao depende de Transferencias simples e pode avancar em paralelo com esse marco.

#### Escopo

- Criar receitas e despesas pelo modo sem repeticao ou pelo modo avancado de Recorrencia.
- Aceitar frequencias diaria, semanal, mensal e anual, com intervalo.
- Encerrar por quantidade, data limite inclusiva ou sem termino.
- Gerar e persistir RRULE canonica a partir de campos estruturados.
- Expandir ocorrencias em consultas e saldos sem horizonte funcional arbitrario.
- Efetivar e individualizar ocorrencias de forma idempotente.
- Editar ou excluir com os escopos `ONLY_THIS` e `THIS_AND_FUTURE`.
- Nao inclui RRULE textual enviada pelo cliente, RFC 5545 completa nem Transferencias recorrentes.

#### Entregue nesta etapa

- Cadastro de Recorrencias nasce planejado, com RRULE canonica gerada pelo backend.
- Recorrencias e Parcelamentos sao cadastrados no mesmo `POST /api/recorrencias`, diferenciados por `tipoGrupo`; edicao, efetivacao e exclusao tambem usam a mesma familia de rotas.
- Ocorrencias virtuais aparecem em `/api/transacoes`, inclusive no Saldo de abertura, sem materializacao durante a consulta.
- Efetivacao e `ONLY_THIS` materializam uma Transacao vinculada ao Segmento; `THIS_AND_FUTURE` exige ocorrencia virtual e divide o Segmento no mesmo Grupo.
- Exclusao individual registra Supressao de recorrencia; `escopo=THIS_AND_FUTURE` encerra o Segmento do corte e os Segmentos ativos posteriores, removendo somente excecoes planejadas.
- A consulta unificada expoe RRULE, DTSTART e politica de datas para o consumidor reproduzir a projecao; a exclusao futura preserva Transacoes materializadas e cancela o Grupo quando elimina toda a projecao virtual, ou o conclui quando preserva ocorrencias virtuais anteriores.

#### Regras criticas e dados

- O inicio corresponde ao padrao escolhido, e sempre a primeira ocorrencia e conta no termino por quantidade.
- Frequencia semanal exige um ou mais dias unicos e usa segunda-feira como inicio da semana. Frequencia mensal exige um ou mais dias unicos entre 1 e 31. Frequencia anual deriva mes e dia do inicio.
- Recorrencias omitem datas mensais ou anuais inexistentes sem deslocamento implicito.
- A RRULE e armazenada sem o prefixo `RRULE:`, com ordem deterministica, valores padrao omitidos, listas ordenadas, semana iniciada na segunda-feira e data limite em formato basico.
- Grupo e Segmento de recorrencia preservam a regra; ocorrencias planejadas permanecem virtuais e consultas nunca as materializam como efeito colateral.
- Uma ocorrencia e persistida quando efetivada, individualizada ou convertida em excecao. Uma Recorrencia criada efetivada persiste apenas a primeira ocorrencia.
- A identidade logica e imutavel de uma ocorrencia combina Segmento de origem e data original e possui unicidade no banco.
- `ONLY_THIS` preserva Grupo, Segmento e data original, cria uma excecao completa e nao altera a RRULE.
- Excluir somente uma ocorrencia virtual registra uma supressao propria, sem criar uma Transacao financeira.
- `THIS_AND_FUTURE` encerra o Segmento anterior antes do corte e, quando houver continuidade, cria outro Segmento no mesmo Grupo.
- Ocorrencias efetivadas posteriores ao corte sao preservadas como excecoes e suprimem projecoes conflitantes. Excecoes apenas planejadas substituidas podem ser removidas.
- O schema permitira referencia ao Segmento em excecoes, identidade unica, supressoes e encerramento de Segmentos. A validacao completa da regra permanece no motor, nao em constraint SQL textual.

#### Concluido quando

- Criacao, validacao, canonicalizacao, expansao, materializacao, edicao e exclusao possuem contratos e casos de uso.
- Consultas e saldos projetam ocorrencias de qualquer periodo solicitado sem grava-las.
- Identidade de ocorrencia e materializacao sao idempotentes e protegidas no banco.
- `ONLY_THIS` e `THIS_AND_FUTURE` preservam ocorrencias efetivadas e nao produzem duplicatas.
- Testes de integracao cobrem frequencias, intervalos, terminos, datas inexistentes, horizonte distante, excecoes, supressoes e segmentacao.
- O glossario e o modelo financeiro registram identidade de ocorrencia, supressao e escopos de edicao.

### 6. Parcelamentos

**Estado:** em implementacao.

#### Objetivo

Representar planos financeiros finitos compostos por Parcelas numeradas, reutilizando o motor de repeticao sem confundir Parcelamento com Recorrencia.

#### Depende de

- Motor de recorrencia.

#### Escopo

- Criar Parcelamento mensal.
- Criar Parcelamento configuravel com frequencia diaria, semanal, mensal ou anual, intervalo e quantidade finita.
- Projetar, consultar, efetivar, editar e suprimir Parcelas.
- Aplicar `ONLY_THIS` e `THIS_AND_FUTURE`.
- Consultar Total contratado original e Total atual do parcelamento.

#### Entregue nesta etapa

- Cadastro finito por valor da Parcela, numero da primeira Parcela e quantidade total original.
- Projecao, efetivacao, edicao individual, divisao futura e supressao preservam a numeracao original.
- Exclusao futura encerra o plano corrente; alterar a quantidade cria novo Grupo e inicia a partir da parcela selecionada.
- O Total contratado original permanece derivavel e o Total atual ainda nao e calculado nem exposto.

#### Regras criticas e dados

- A autoridade monetaria inicial e o valor por Parcela, nao um total a dividir.
- A entrada informa numero da primeira Parcela e quantidade total original. Quantidade restante, ultima Parcela e Total contratado original sao derivados.
- Cadastrar uma Parcela intermediaria projeta somente ela e as posteriores, preservando a numeracao original.
- Numeracao e quantidade total original permanecem estaveis depois de edicoes e supressoes.
- O Total contratado original e o valor original por Parcela multiplicado pela quantidade total original. O Total atual do parcelamento e a soma das Parcelas existentes.
- O modo mensal gera uma Parcela por mes. O modo configuravel usa uma unica cadencia ancorada no inicio e nao aceita listas de varios dias semanais ou mensais.
- Parcelamentos mensais e anuais ajustam datas inexistentes para o ultimo dia do mes, sem omitir Parcelas.
- Uma edicao individual cria excecao sem alterar numeracao. Uma edicao futura cria novo Segmento no mesmo Grupo e preserva Parcelas efetivadas posteriores.
- Suprimir uma ou mais Parcelas nao renumera as demais.
- Alterar a quantidade encerra o Parcelamento atual e cria outro Grupo, preservando o denominador e o Total contratado original anteriores.
- O schema distingue Parcelamento de Recorrencia e persiste numero inicial, quantidade total original e dados monetarios necessarios para reconstruir numeracao e totais estaveis.

#### Concluido quando

- Modos mensal e configuravel possuem contratos e casos de uso.
- Projecao, materializacao, edicao e supressao preservam numeracao e totais.
- Datas inexistentes seguem a semantica propria de Parcelamento sem omitir Parcelas.
- O schema reconstrui o contrato original e diferencia Parcelamento de Recorrencia.
- Testes de integracao cobrem inicio intermediario, cadencias, datas inexistentes, totais, escopos de edicao, supressoes e alteracao de quantidade.
- Glossario e modelo financeiro refletem a semantica entregue.

## Gate global do MVP

O MVP esta concluido somente quando:

- Todos os marcos do MVP estiverem concluidos conforme suas dependencias.
- O comportamento entregue estiver refletido no roadmap, no glossario, no modelo financeiro e na apresentacao do projeto.
- Migrations incrementais, constraints, triggers e mapeamentos JPA estiverem validados contra PostgreSQL sem alterar a migration inicial compartilhada.
- Todos os recursos estiverem isolados pelo Usuario autenticado e nao revelarem a existencia de dados alheios.
- Contratos HTTP e invariantes financeiras criticas possuirem testes de integracao.
- `mvnw verify -B` estiver aprovado no ambiente de verificacao.

## Depois do MVP

A ordem abaixo e uma prioridade sugerida, nao uma sequencia estrita. Uma capacidade pode ser antecipada quando suas dependencias estiverem satisfeitas.

### Orcamentos

**Depende de:** MVP concluido, Categorias, consultas de Transacoes e Saldo de abertura.

- Definir limites por Categoria e periodo.
- Acompanhar realizado e projetado.
- Alertar consumo dos limites.

### Cartao de credito

**Depende de:** MVP concluido, Transacoes e Parcelamentos.

- Representar cartoes, limites, compras, Parcelamentos, faturas e pagamentos.
- Controlar fechamento e vencimento de faturas.
- Integrar compras e pagamentos aos saldos.

### Relatorios

**Depende de:** MVP concluido. Pode avancar antes de Orcamentos e Cartao de credito e incorporar seus dados quando existirem.

- Produzir graficos, filtros e comparacoes por periodo.
- Consolidar dados por Conta financeira e Categoria.
- Exportar dados.

### Analises financeiras assistidas por IA

**Depende de:** Relatorios.

- Analisar dados financeiros e apresentar explicacoes ou recomendacoes.
- Permanecer em modo consultivo.
- Nao criar, alterar, efetivar ou excluir operacoes financeiras.

## Capacidades futuras sem prioridade fechada

### Conversao entre moedas

**Depende de:** politica cambial e calculo de saldos.

- Permitir Contas financeiras em outras moedas.
- Converter valores por uma politica cambial explicita.

### Divisao por Categorias

**Depende de:** Transacoes e Categorias.

- Classificar uma Transacao em mais de uma Categoria.
- Preservar a soma e a integridade das divisoes.

### Compartilhamento de dados

**Depende de:** regras proprias de autorizacao e isolamento.

- Compartilhar dados financeiros entre Usuarios autorizados.
- Preservar propriedade, escopo de acesso e privacidade.
