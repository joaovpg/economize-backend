# Contratos unificados de transações e transferências

Status: done

## Objetivo e contexto

Oferecer um fluxo único de cadastro e edição de receitas e despesas, com repetição opcional e parcelamento como configuração adicional da repetição. Transferências têm fluxo de escrita próprio e não se repetem. A leitura permanece unificada. Este documento descreve o contrato pretendido, não o comportamento já implementado.

O sistema atual separa escritas em transações, recorrências e transferências; usa segmentos, RRULE, ocorrências virtuais, exceções e supressões. O contrato proposto substitui as escritas antigas sem exigir compatibilidade temporária. O frontend será ajustado pelo usuário após os testes. Preserve isolamento por usuário, regras financeiras e convenções de `CONTEXT.md`, `docs/modelo-financeiro.md` e ADRs, atualizando a documentação de domínio e registrando decisões arquiteturais pertinentes quando implementado.

## Histórias de usuário

1. Como pessoa usuária, quero cadastrar uma receita ou despesa simples para registrar um movimento único.
2. Como pessoa usuária, quero configurar uma repetição sem término ou com limite para projetar movimentos futuros.
3. Como pessoa usuária, quero registrar parcelas numeradas para acompanhar um compromisso finito.
4. Como pessoa usuária, quero converter uma transação simples em repetição ou parcelamento para não recadastrar o movimento existente.
5. Como pessoa usuária, quero editar uma única ocorrência ou as próximas para corrigir os dados no alcance desejado.
6. Como pessoa usuária, quero converter uma ocorrência em simples ou em outra repetição para ajustar um compromisso sem perder o histórico.
7. Como pessoa usuária, quero converter repetição em parcelamento e vice-versa para adaptar o plano à realidade.
8. Como pessoa usuária, quero preservar ocorrências individualizadas ou efetivadas durante alterações em lote para não perder meus ajustes ou histórico.
9. Como pessoa usuária, quero visualizar e resolver sobreposições antes de salvar para controlar os impactos no saldo.
10. Como pessoa usuária, quero ser avisada quando outra alteração tornar minha edição desatualizada para não sobrescrever dados.
11. Como pessoa usuária, quero cadastrar e editar transferências para movimentar valores entre minhas contas.
12. Como pessoa usuária, quero consultar movimentos, parcelas, transferências e saldos por período para acompanhar minhas finanças.
13. Como pessoa usuária, quero consultar datas distantes de repetições indefinidas para planejar meu saldo futuro.
14. Como pessoa usuária, quero que falhas de validação não apliquem parte de uma operação financeira para manter meus dados consistentes.

## Princípios comuns

- JSON usa nomes e valores de domínio em português, inclusive `SOMENTE_ESTA` e `ESTA_E_AS_PROXIMAS`; não oferecer aliases ingleses dos escopos antigos.
- Escritas usam valor nominal positivo, `BigDecimal`, até quatro casas decimais e capacidade compatível com o schema. Na linha do tempo, valor é o impacto assinado.
- `PLANEJADA` e `EFETIVADA` impactam igualmente os saldos derivados. Efetivar exige data não futura no fuso do usuário; replanejar limpa o instante de efetivação; correção de uma efetivada preserva esse instante.
- Autorização e validação de contas e categorias seguem as regras existentes, inclusive recursos inativos já associados, data do saldo inicial e recursos de terceiros indistinguíveis dos inexistentes.
- A RRULE é um detalhe de implementação; a configuração estruturada deve representar sem perda todas as regras já aceitas e existentes. Datas financeiras são datas civis; instantes usam ISO 8601 com fuso UTC.
- Campos obrigatórios não podem ser omitidos; campos declarados anuláveis aceitam `null`. A representação completa do PUT exige os campos de escrita, incluindo opcionais anuláveis, para tornar explícita sua remoção.

## Contratos HTTP

### POST /api/transacoes

Recebe um objeto com `tipo` (`RECEITA` ou `DESPESA`), `contaId`, `categoriaId` anulável, `descricao`, `observacoes` anulável, `valor`, `dataFinanceira`, `situacao` e `repeticao` anulável. Respeitar os limites atuais de descrição e observações e demais validações existentes.

`repeticao: null` cria Transação simples. Caso contrário, recebe `frequencia` (`DIARIA`, `SEMANAL`, `MENSAL`, `ANUAL`), `intervalo` inteiro positivo, `diasSemana`, `diasMes`, `politicaData`, `termino` e `parcelamento`. Os dias, quando aplicáveis, devem expressar o subconjunto de RRULE suportado; usar dias da semana em português. Validar combinações conforme o motor e preservar sem perda o significado das regras existentes.

`politicaData` é `PADRAO` ou `AJUSTAR_ULTIMO_DIA_MES`. A interface pode sugerir a primeira para repetição comum e a segunda para parcelamento; o valor efetivo é explícito e devolvido. `PADRAO` respeita as datas válidas da regra; o ajuste usa o último dia do mês quando a data mensal ou anual não existe.

Sem parcelamento, `parcelamento` é nulo e `termino` é um dos objetos `{"tipo":"INDEFINIDO"}`, `{"tipo":"EM_DATA","data":"2027-11-10"}` ou `{"tipo":"APOS_OCORRENCIAS","quantidade":12}`. A data final é inclusiva; a quantidade é positiva. Com parcelamento, `termino` é nulo e `parcelamento` contém `numeroPrimeiraParcela` e `totalParcelas`, inteiros positivos, com primeira parcela não superior ao total. O número de ocorrências restantes é `totalParcelas - numeroPrimeiraParcela + 1`. O valor informado é por parcela; o total contratado original é derivado e não equivale necessariamente à soma atual após alterações e supressões.

`dataFinanceira` é a primeira data da série, compatível com a regra e o saldo inicial da conta. `situacao` define apenas a primeira ocorrência: se efetivada, registrar o instante; futuras são planejadas. Criação com repetição efetivada exige materializar a primeira ocorrência sem gerar cópia virtual.

Resposta `201 Created`: representação completa da transação ou primeira ocorrência, conforme o item de GET, com `chaveEdicao` e `versaoEdicao`. A resposta deve permitir identificar o recurso para edição; ocorrência virtual não requer UUID de transação.

### PUT /api/transacoes/{chaveEdicao}

Recebe todos os campos do POST e `versaoEdicao`, mais `escopo` obrigatório para origem de série e omitido para Transação simples. `escopo` aceita `SOMENTE_ESTA` ou `ESTA_E_AS_PROXIMAS`. A chave opaca é fornecida pelo GET ou pela resposta de escrita, tem codificação segura para segmento de URL e não revela dados financeiros; não é autorização. É estável enquanto a identidade editável não for substituída. A chave deve identificar também ocorrência virtual pela identidade interna segmento e data original; a data financeira alterada não muda a data original. Após substituição de identidade, resposta devolve chave vigente; chave antiga sem recurso editável resulta em 404 sem revelar propriedade.

A versão opaca recebida cobre tanto a ocorrência como alterações relevantes na série, inclusive ocorrência materializada, supressão ou alterações que modifiquem uma prévia. Uma escrita concorrente ou versão obsoleta retorna 409 `VERSAO_DESATUALIZADA`; comparar e aplicar atomicamente. Evitar que uma versão global cause conflito entre usuários. A verificação de dono precede informações que revelem existência.

`situacao` se aplica apenas à ocorrência selecionada, inclusive em escopo futuro. O usuário pode editar explicitamente uma ocorrência individualizada ou efetivada, preservando o instante enquanto continuar efetivada. Em mudanças de série, materializadas individualizadas e efetivadas posteriores são protegidas: não são alteradas em lote. A alteração é atômica.

Matriz de conversão:

| Origem | Destino | Efeito |
| --- | --- | --- |
| Simples | Repetição, parcelada ou não | Transação existente é primeira ocorrência; preserva situação e instante quando não alterados; não duplicar. |
| Série | Simples, `SOMENTE_ESTA` | Individualiza como simples e impede reaparecimento da ocorrência original; série segue. |
| Série | Simples, `ESTA_E_AS_PROXIMAS` | Selecionada torna-se simples; encerra projeção antiga a partir do corte; preserva ocorrências protegidas posteriores. |
| Série | Nova repetição ou parcelamento, `SOMENTE_ESTA` | Selecionada inicia nova série; suprimir origem para evitar reaparecimento; série anterior continua. |
| Série | Repetição ou parcelamento, `ESTA_E_AS_PROXIMAS` | Corta projeção antiga e inicia novo trecho/configuração; histórico e ocorrências protegidas são preservados. |
| Parcelamento | Parcelamento | Preserve numeração e total original; mudança do total encerra plano anterior e cria novo grupo, conforme modelo atual. |

Em recorrência → parcelamento, exigir número da primeira parcela e total; em parcelamento → recorrência, exigir término novo e conservar numeração anterior no histórico. Aplicar também a ocorrência selecionada materializada/efetivada. Definir o corte pela data original, não por data financeira deslocada; preservar vínculo de origem necessário para histórico e evitar reaparecimento sem sujeitar registros protegidos a edições em lote. Datas da nova regra devem ser válidas; nunca alterar a data financeira de registros protegidos automaticamente.

Resposta `200 OK`: representação completa e nova chave/versão vigentes. O PUT deve realizar as decisões de sobreposição abaixo na mesma transação.

### Prévia e sobreposições

`POST /api/transacoes/{chaveEdicao}/previa-edicao` recebe o corpo proposto do PUT, incluindo escopo e versão, e período de prévia de 1 a 12 meses civis inclusivos. Não grava nem altera saldos. Devolve versão analisada, período, itens preservados e candidatos da nova regra no período, impactos assinados, sobreposições e decisões possíveis. Uma sobreposição existe quando uma nova ocorrência e uma ocorrência protegida posterior abrangida pelo corte têm a mesma **data financeira**. A própria ocorrência selecionada é tratada como a primeira ocorrência resultante e não concorre consigo mesma. Uma ocorrência antiga deslocada de 10 para 12 só se sobrepõe a candidato no dia 12; a identidade antiga ainda usa a data original 10. Datas próximas ou somente a mesma data original não constituem sobreposição.

Para cada data candidata com sobreposição na prévia, permitir `MANTER_SOMENTE_PRESERVADA` ou `MANTER_AMBAS`; recomendar a primeira. O PUT recebe escolhas específicas identificadas pela data original candidata, além da política geral `MANTER_SOMENTE_PRESERVADA` para sobreposições sem escolha específica, inclusive fora da prévia e séries indefinidas. Escolhas específicas inválidas ou não correspondentes são rejeitadas. A preservada nunca é alterada pela resolução. Cada candidato suprimido é persistido de forma que não reapareça após reinício ou consultas posteriores; manter ambas produz dois impactos. Caso existam várias preservadas no mesmo dia, a decisão deve se referir ao candidato e todas as preservadas permanecem; suprimir remove somente esse candidato.

A prévia é informativa e o PUT revalida versão, propriedade, conflitos e escolhas na mesma transação. Uma prévia desatualizada retorna 409; falha não grava alterações parciais. A consulta de prévia limitada não deve truncar a aplicação da política geral às preservadas fora da janela. A regra de colisão se aplica às ocorrências protegidas já existentes no momento da operação, e não elimina automaticamente futuras transações independentes criadas depois.

### POST e PUT /api/transferencias

`POST /api/transferencias` e `PUT /api/transferencias/{transferenciaId}` recebem `contaOrigemId`, `contaDestinoId`, `descricao`, `observacoes` anulável, `valor` positivo, `dataFinanceira` e `situacao`. PUT recebe ainda `versaoEdicao`, devolvida na leitura e na resposta; versão desatualizada retorna 409. POST retorna `201 Created`; PUT retorna `200 OK`; ambos devolvem a representação completa, ID, versão e `efetivadoEm` anulável.

Transferência não aceita categoria nem repetição. Contas devem pertencer ao usuário, ser diferentes e usar mesma moeda. Nova associação exige conta ativa; manutenção de associação existente em conta inativa é permitida. Respeitar saldos iniciais e regra de efetivação. Os dois lados e o registro da operação são criados ou alterados atomicamente; seus dados comuns e instante permanecem iguais.

### GET /api/transacoes

Mantém período inclusivo `inicio` e `fim` em `yyyy-MM`, de 1 a 12 meses, filtros existentes por contas e categorias, `saldoAbertura` e itens ordenados. Saldo de abertura e impactos seguem `docs/modelo-financeiro.md`; itens de saldo inicial não são transações editáveis. A consulta expande somente o necessário para o período e o saldo de abertura, inclusive série indefinida, sem persistir ocorrências por efeito de leitura.

Cada item retorna `origem`, `operacaoId` anulável para virtual, `chaveEdicao` e `versaoEdicao` anuláveis para itens não editáveis, `virtual`, `tipo` de transação quando aplicável, `situacao`, `descricao`, `observacoes`, `valor` assinado, `dataFinanceira`, `efetivadoEm`, `contaId`, `categoriaId`, `contaContraparteId`, `serie`, `repeticao` e `escoposDisponiveis`. Campos não aplicáveis são nulos; `escoposDisponiveis` é uma lista vazia fora de séries. Ocorrência virtual tem situação `PLANEJADA`; transferência usa ID da operação nos dois lados e versão da operação; saldo inicial não possui chave, versão, situação ou tipo de transação.

Para série, `serie` contém `grupoId`, `segmentoId`, `dataOriginal`, `numeroParcela` e `totalParcelas` anuláveis. `repeticao` contém toda a configuração estruturada editável, inclusive política de datas e término indefinido, e deve preservar a semântica de regras existentes. Itens protegidos mantêm origem suficiente para rastreabilidade e explicitação de suas opções de edição. `escoposDisponiveis` reflete opções efetivamente aceitas, inclusive ocorrência selecionada materializada ou efetivada quando suportado.

Transferências aparecem por seus lados com impactos opostos; filtro de categoria exclui transferências. Valores de saldo inicial preservam seu sinal. Não expor entidades JPA nem IDs internos dos lados de transferência.

## Erros e segurança

Erros usam `application/problem+json` conforme RFC 9457 e códigos estáveis em português. Entrada malformada ou campo ausente retorna 400 com `errors` quando aplicável; violação de regra de negócio retorna 422; versão ou prévia desatualizada retorna 409; recurso inexistente ou de outro usuário retorna 404. Autenticação, cookie e CSRF seguem os contratos existentes. Não registrar chaves, corpos ou dados financeiros em logs. Validar campos não aplicáveis, combinações inválidas de término e parcelamento, datas e regras antes de gravar.

## Implementação e persistência

Manter limites por domínio e fluxo Resource → mapper → caso de uso → repository. A operação unificada orquestra regras de transação e repetição sem duplicá-las; transferência preserva atomicidade própria. A conversão, supressões, versões e conflitos exigem proteção transacional e de concorrência efetiva no banco, não só checagem em memória. Examinar constraints e triggers antes de alterar vínculos; Flyway é autoridade de schema, migrations incrementais somente se necessárias, sem alterar V1. Se a nova representação exigir novos vínculos, versões ou registros de supressão, migrar dados antigos preservando histórico, numeração, data original e políticas de datas. Não modificar `target/`.

## Verificação e critérios de aceite

Testar nos contratos HTTP públicos com `@QuarkusTest`, REST Assured e PostgreSQL real; testes de migration seguem o padrão de schemas isolados. Verificar criação e edição de todos os tipos, contas e categorias de terceiros/inativas, valores, datas e situação; simples ↔ série, série ↔ parcelamento e ambos os escopos; seleção virtual, individualizada e efetivada; preservação de exceções e efetivadas posteriores; nenhuma duplicação entre projeção, materialização e supressão; cortes em datas original e financeira distintas; mudança de frequência com colisões, múltiplas preservadas, ambas as decisões e política fora da janela; versão obsoleta entre prévia e PUT e concorrência entre escritores; rollback em falha; numeração e totais de parcelas; ambas políticas para dias 29–31; saldo de abertura, filtros, dois lados da transferência; recorrência indefinida em consultas próximas e distantes sem expansão infinita; regras antigas convertidas sem perda; isolamento entre usuários; e OpenAPI consistente.

Executar `./mvnw.cmd verify -B` no Windows antes de concluir implementação. Não há bug específico de recorrência indefinida reproduzido nesta especificação; os cenários são critérios de comportamento, não diagnóstico de causa.

## Fora de escopo

Transferências recorrentes ou parceladas; conversões entre transferência e transação; modificações do frontend; manutenção de compatibilidade das antigas rotas de escrita ou escopos ingleses; exclusão de itens como nova funcionalidade deste contrato; identificação ou correção de bug sem reprodução específica; cálculo de total atual do parcelamento como obrigação desta entrega.

## Detalhamento normativo dos contratos

As regras desta seção concretizam os campos acima e prevalecem sobre qualquer exemplo abreviado. Nomes não listados nos objetos de escrita são rejeitados como campos desconhecidos, inclusive campos de parcelamento na transferência. Strings vazias não substituem `null`; datas usam `yyyy-MM-dd`, meses usam `yyyy-MM` e IDs usam UUID textual.

### Configuração estruturada de repetição

`repeticao` contém exatamente `frequencia`, `intervalo`, `diasSemana`, `diasMes`, `politicaData`, `termino` e `parcelamento`. Os conjuntos são arrays obrigatórios sem duplicatas, sempre vazios quando inaplicáveis; a leitura ordena dias da semana de segunda a domingo e dias de mês em ordem crescente. Valores de `diasSemana`: `SEGUNDA`, `TERCA`, `QUARTA`, `QUINTA`, `SEXTA`, `SABADO`, `DOMINGO`. `diasMes` contém inteiros de 1 a 31. `intervalo` e quantidades estão entre 1 e 2.147.483.647, sujeitos também à representação de datas e às validações do motor.

- `DIARIA`: ambos os arrays vazios.
- `SEMANAL`: `diasSemana` não vazio e `diasMes` vazio; inclui o dia da semana da data de início.
- `MENSAL`: `diasSemana` vazio; sem parcelamento com `PADRAO`, `diasMes` não vazio e inclui o dia da data de início. Com parcelamento, array vazio e cadência ancorada no dia inicial.
- `ANUAL`: ambos os arrays vazios; mês e dia derivam da data de início.
- `AJUSTAR_ULTIMO_DIA_MES` é aceito apenas para `MENSAL` ou `ANUAL` com ambos os arrays vazios; a data de início ancora o dia. Não combinar ajuste com múltiplos dias. `PADRAO` preserva as regras do motor, incluindo regras mensais com vários dias.
- No parcelamento semanal, a cadência tem um único dia da semana, correspondente à data de início; no diário, mensal e anual seguem as regras acima. A escolha `PADRAO` pode pular datas inexistentes; o ajuste só é possível nas combinações indicadas.

Em criação de série, o início deve ser a primeira ocorrência conforme as validações atuais: em semana com vários dias, nenhum dia pode preceder o dia de início; em mês com vários dias, nenhum dia pode ser anterior ao início. Na leitura de segmentos existentes cujo início é uma ocorrência válida mas não a primeira posição da semana ou mês, devolver a configuração e `inicioSegmento` reais; ao editar a série, validar o novo início como uma ocorrência da regra, sem exigir que seja o menor dia da semana ou mês. Preservar a regra anterior sem recalcular ou perder dias ao atualizar apenas dados financeiros.

`termino` é objeto discriminado estrito: `INDEFINIDO` sem outros campos, `EM_DATA` com `data`, `APOS_OCORRENCIAS` com `quantidade`. Com `parcelamento` não nulo, `termino` deve ser nulo; `parcelamento` contém exatamente `numeroPrimeiraParcela` e `totalParcelas`. O valor de cada ocorrência é positivo. `APOS_OCORRENCIAS` conta ocorrências do segmento a partir de `inicioSegmento`, incluindo a primeira, independentemente de posterior supressão. A quantidade restante do parcelamento conta posições da mesma maneira; não renumerar após supressões.

Exemplo de criação de série mensal indefinida:

```json
{
  "tipo":"DESPESA", "contaId":"00000000-0000-0000-0000-000000000001",
  "categoriaId":null, "descricao":"Aluguel", "observacoes":null,
  "valor":1500.00, "dataFinanceira":"2026-11-10", "situacao":"PLANEJADA",
  "repeticao":{
    "frequencia":"MENSAL", "intervalo":1, "diasSemana":[], "diasMes":[10],
    "politicaData":"PADRAO", "termino":{"tipo":"INDEFINIDO"}, "parcelamento":null
  }
}
```

### Forma da prévia e do PUT

A prévia recebe objeto com `alteracao`, `periodo` e `politicaSobreposicao`. `alteracao` é a representação completa do PUT sem `resolucoesSobreposicao` e sem `politicaSobreposicao`; `periodo` contém `inicio` e `fim` como meses inclusivos; `politicaSobreposicao` aceita somente `MANTER_SOMENTE_PRESERVADA`. Em alteração de transação simples, `escopo` é omitido dentro de `alteracao`.

A prévia responde `200` com `versaoEdicao`, `periodo`, `politicaSobreposicao`, `itensPreservados`, `ocorrenciasNovas` e `sobreposicoes`. Cada item preservado é um item de leitura; cada candidata contém `dataOriginal`, `dataFinanceira`, `valor` assinado e `numeroParcela` anulável. Cada sobreposição contém `dataOriginalCandidata`, `dataFinanceira`, `chavesPreservadas` não vazia, `resolucaoSugerida` e `resolucoesPermitidas`. São relatadas apenas sobreposições cuja data financeira esteja no período; ocorrências preservadas anteriores ao corte não participam. Ordenar candidatos e sobreposições por data financeira e data original; chaves preservadas em ordem lexicográfica. A prévia também devolve `quantidadeSobreposicoesForaPeriodo` como contagem exata dos candidatos em colisão com registros protegidos existentes fora do período, pois o conjunto de registros persistidos é finito; não expandir uma repetição infinita sem limite.

O PUT acrescenta ao objeto de alteração os campos obrigatórios `politicaSobreposicao` e `resolucoesSobreposicao`, este último um array de objetos `{ "dataOriginalCandidata":"2026-12-10", "resolucao":"MANTER_AMBAS" }`. Aceitar cada data candidata no máximo uma vez; cada data deve ser uma sobreposição da alteração calculada na versão enviada. Se nenhuma escolha for informada, enviar `[]`; aplica-se a política geral. O par política e lista é obrigatório em edição que cria um novo trecho; em demais edições deve ser omitido. Para uma data com várias preservadas, a resolução se aplica ao candidato inteiro, nunca a uma preservada em particular. As escolhas substituem a política geral para as datas informadas.

Exemplo de prévia para alterar série:

```json
{
  "alteracao": {
    "versaoEdicao":"versao-opaca", "escopo":"ESTA_E_AS_PROXIMAS",
    "tipo":"DESPESA", "contaId":"00000000-0000-0000-0000-000000000001",
    "categoriaId":null, "descricao":"Aluguel", "observacoes":null,
    "valor":1500.00, "dataFinanceira":"2026-11-10", "situacao":"PLANEJADA",
    "repeticao":{
      "frequencia":"MENSAL", "intervalo":1, "diasSemana":[], "diasMes":[10],
      "politicaData":"PADRAO", "termino":{"tipo":"INDEFINIDO"}, "parcelamento":null
    }
  },
  "periodo":{"inicio":"2026-11", "fim":"2027-10"},
  "politicaSobreposicao":"MANTER_SOMENTE_PRESERVADA"
}
```

Exemplo do fragmento de resposta:

```json
{
  "versaoEdicao":"versao-opaca", "periodo":{"inicio":"2026-11", "fim":"2027-10"},
  "politicaSobreposicao":"MANTER_SOMENTE_PRESERVADA",
  "itensPreservados":[], "ocorrenciasNovas":[],
  "sobreposicoes":[{
    "dataOriginalCandidata":"2026-12-10", "dataFinanceira":"2026-12-10",
    "chavesPreservadas":["chave-opaca-preservada"],
    "resolucaoSugerida":"MANTER_SOMENTE_PRESERVADA",
    "resolucoesPermitidas":["MANTER_SOMENTE_PRESERVADA","MANTER_AMBAS"]
  }], "quantidadeSobreposicoesForaPeriodo":0
}
```

O fragmento ilustra a estrutura da sobreposição; em uma resposta real as listas de preservadas e candidatas também contêm os itens correspondentes. Não se exige token de prévia nem persistência de prévia: o servidor recalcula e valida as escolhas sob o mesmo bloqueio e versão no PUT. A prévia é opcional; sem escolhas, aplica-se a política geral. Limitar tamanho do corpo e da lista de escolhas conforme limites de requisição configurados, informando erro 400; não truncar silenciosamente.

### Identidade, versão e concorrência

`chaveEdicao` é string não vazia URL-safe, opaca e versionada no seu formato interno; conter tipo de identidade e UUID da transação simples ou do segmento mais data original, codificados sem dados financeiros. A codificação não é segredo nem autorização. Para ocorrência protegida desligada da série, usar identidade da transação persistida e preservar metadados de origem separadamente. Quando a materialização não substituir a identidade lógica de uma ocorrência da série, a chave de segmento e data permanece estável; conversão ou corte podem substituí-la. A chave devolvida para a ocorrência resultante deve sempre ser utilizável imediatamente.

`versaoEdicao` é string opaca gerada pelo servidor. Para simples e transferência, corresponde à versão persistida do agregado; para qualquer ocorrência associada a uma série, contém a revisão compartilhada do grupo e a versão do registro selecionado, se existente. Cada mutação que possa afetar a série, exceções, supressões, projeção, situação, dados ou resolução de conflitos incrementa a revisão do grupo, inclusive criações e exclusões de ocorrências protegidas. Para operações que afetem dois grupos, bloquear e revisar ambos em ordem determinística de UUID. Uma ocorrência destacada que conserve vínculo histórico deve participar da revisão da origem enquanto mudanças nela puderem alterar colisões ou a prévia da série. Escritas de transação simples e transferência são protegidas por versão otimista ou bloqueio equivalente. Na gravação de série, obter bloqueio pessimista do grupo, reconsultar e comparar a versão; gravar tudo e incrementar revisão atomicamente. Mapear perda de corrida de versão para 409. Consultas de prévia usam uma visão consistente e nunca autorizam gravação sem nova comparação. Mudanças em conta/categoria entre prévia e PUT são revalidadas no PUT.

### Estados de edição e respostas

`serie` contém `grupoId`, `segmentoId`, `inicioSegmento`, `dataOriginal`, `numeroParcela` e `totalParcelas`; os dois últimos são nulos para repetição sem parcelas. Ocorrências destacadas da repetição têm `repeticao: null`, mas podem manter `serie` histórico e um campo booleano `protegida: true`. Uma ocorrência efetivada ou individualizada protegida também tem `protegida: true`; virtuais não protegidas têm `false`. No saldo inicial, `virtual` e `protegida` são nulos; em transferência ambos são `false`; em transação simples não destacada ambos são `false`. Uma ocorrência destacada editável exclusivamente como simples devolve `escoposDisponiveis: []`; ocorrência ainda associada que aceite corte devolve ambos os escopos. A API não deduz `repeticao` do histórico em uma ocorrência destacada.

Para evitar contradição entre histórico e origem no GET, usar `origem: TRANSACAO_SIMPLES` para ocorrência destacada simples e sua chave da transação; `serie` histórico não implica que ela receba alterações da série. Parcelas preservadas mantêm número e total históricos. Uma ocorrência protegida ainda associada pode ser selecionada explicitamente para criar novo trecho com `ESTA_E_AS_PROXIMAS`; seu próprio conteúdo é alterado apenas pelo PUT explícito.

Criação e edição de transação respondem com o mesmo objeto de item do GET, com os campos completos e valor **assinado**, inclusive quando é a primeira ocorrência virtual. A criação/edição de transferência responde com `transferenciaId`, `versaoEdicao`, `contaOrigemId`, `contaDestinoId`, `descricao`, `observacoes`, `valor` nominal positivo, `dataFinanceira`, `situacao` e `efetivadoEm`. O `GET` de cada lado fornece também a versão e a conta contraparte, permitindo reconstruir origem e destino pelo sinal de impacto. A resposta de criação inclui `Location` apontando para a rota de edição do recurso; nenhuma operação GET individual é presumida.

### Compatibilidade e migração

A migration incremental seguinte à maior versão existente no momento da implementação adiciona política de data por segmento e os elementos necessários à revisão da série e identidade/origem de ocorrências destacadas. Não editar V1 ou qualquer migration já aplicada; inspecionar migrations concorrentes antes de escolher o número. Preencher política dos segmentos existentes a partir do tipo de grupo: `RECORRENCIA` → `PADRAO`, `PARCELAMENTO` → `AJUSTAR_ULTIMO_DIA_MES`. Não alterar RRULE, início, fim, contagem, numeração, supressões, instantâneos ou valores históricos. A política é por segmento para preservar o histórico quando a modalidade muda. A revisão da série é inicializada deterministicamente e incrementada nas alterações posteriores; versões já existentes em `VER_REGISTRO` podem apoiar o mecanismo, desde que todas as mutações incrementem a revisão compartilhada.

Antes de migrar, verificar regras existentes com o leitor real, inclusive início de segmento que não é primeiro dia da semana/mês e segmentos mensais antigos sem `BYMONTHDAY`. Na leitura, traduzir todos os casos válidos sem mudança de datas; aceitar no PUT uma configuração retornada pela API sem obrigar mudança de regra, inclusive a forma mensal ancorada sem dias para segmentos já existentes. Essa forma usa `diasMes: []` e `PADRAO`; para novas séries comuns com `PADRAO`, continua obrigatório informar dias do mês. Testar migração a partir do schema imediatamente anterior, com recorrência indefinida, exceção deslocada, supressão, parcela, múltiplos segmentos e efetivada; comparar ocorrências, saldos e metadados antes/depois. Respeitar constraints de proprietário, unicidade de ocorrência e trigger de transferência, revisando-as por migration quando necessário. Falhas na migração não podem descartar dados.

Remover ou substituir rotas antigas de escrita de recorrência e os valores públicos ingleses de escopo após implementação dos novos contratos, mantendo explicitamente acessíveis as operações de exclusão e de efetivação/replanejamento necessárias ao ciclo de vida existente até que sejam cobertas pelo novo contrato. O PUT cobre efetivação e replanejamento; exclusão continua disponível e deve seguir as novas regras de revisão e proteção. Não remover rotas de exclusão sem providenciar contrato equivalente e atualizar OpenAPI e testes.

### Códigos de erro adicionais

- `400 DADOS_INVALIDOS`: JSON malformado, campos desconhecidos, campos ausentes ou formato inválido; incluir `errors` para campos quando possível.
- `404 RECURSO_NAO_ENCONTRADO`: chave inválida, recurso inexistente, desativado como identidade de edição ou alheio; mesma resposta sem revelar propriedade.
- `409 VERSAO_DESATUALIZADA`: versão diferente após revalidação ou conflito otimista; a resposta não inclui dados financeiros atuais.
- `422 REPETICAO_INVALIDA`: frequência, dias, política, início ou término incompatíveis.
- `422 PARCELAMENTO_INVALIDO`: numeração, total ou combinação com término inválidos.
- `422 ESCOPO_INVALIDO`: escopo ausente, não aplicável ou não suportado para o recurso.
- `422 RESOLUCAO_SOBREPOSICAO_INVALIDA`: decisão duplicada, resolução desconhecida ou data que não é candidata sobreposta.
- Demais erros financeiros e de autorização seguem os códigos atuais; documentar o conjunto final no OpenAPI e manter o formato RFC 9457.
