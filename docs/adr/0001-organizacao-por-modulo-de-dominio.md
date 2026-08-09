# ADR 0001: Organizacao por modulo de dominio

## Status

Aceita.

## Contexto

O Economize e um monolito modular. Uma estrutura obrigatoria de camadas para cada funcionalidade criaria packages e classes sem comportamento real durante o MVP, espalhando uma unica mudanca entre artefatos cerimoniais.

## Decisao

O primeiro nivel de organizacao do codigo representa modulos de dominio, como `transacao`, `conta` e `categoria`.

Cada modulo comeca plano. Entradas HTTP, casos de uso, dominio e persistencia permanecem no mesmo package enquanto isso favorecer a navegacao. Quando o volume prejudicar a localidade, o modulo pode evoluir para:

```text
transacao/
|-- http/
|   |-- dto/
|   |   |-- request/
|   |   `-- response/
|   `-- TransacaoHttpMapper.java
|-- application/
|-- domain/
`-- persistence/
```

Subpackages sao criados sob demanda, nao como estrutura vazia. Casos de uso recebem nomes de intencao, como `CriarTransacao`, em vez de um `TransacaoService` generico.

DTOs pertencem ao adapter que define o contrato. DTOs HTTP ficam sob `http/dto`, nao conhecem repositories nem entidades JPA. Resources validam o contrato HTTP, obtem o contexto de autenticacao, convertem requests em comandos, invocam um caso de uso e convertem resultados em responses.

Mappers ficam junto ao adapter e usam MapStruct como beans CDI, inclusive para mapeamentos pequenos. Seus metodos tecnicos usam nomes em ingles, como `toCommand`, `toResponse` e `toEntity`. Eles apenas traduzem representacoes e nao acessam repositories nem executam regras de negocio.

Casos de uso recebem comandos e devolvem resultados sem depender de DTOs HTTP. Quando a operacao apenas persiste e retorna uma entidade sem composicao adicional, o caso de uso pode devolver a entidade JPA para o adapter; quando combina dados, aplica transformacoes relevantes ou precisa representar algo que nao coincide com uma entidade, deve devolver um objeto de resultado proprio. Em ambos os casos, o caso de uso valida invariantes, verifica autorizacao sobre recursos, compoe entidades e coordena a persistencia dentro da transacao. Entidades JPA sao modelos passivos de persistencia e nao contem factories, validacoes ou regras de negocio.

## Consequencias

- Uma funcionalidade pode atravessar HTTP, aplicacao, dominio e persistencia sem perder a localidade do modulo.
- O package plano evita abstracoes antecipadas durante o MVP.
- Novos subpackages exigem responsabilidade concreta.
- Entidades JPA e repositories permanecem detalhes internos; uma entidade pode atravessar o limite entre caso de uso e adapter para ser convertida, mas nunca forma diretamente um contrato HTTP.
- Regras de negocio possuem uma unica implementacao nos casos de uso, sem duplicacao nos resources ou nas entidades JPA.
- A geracao dos mappers e verificada em compilacao pelo MapStruct.
