# Economize

[![CI](https://github.com/joaovpg/economize/actions/workflows/ci.yml/badge.svg)](https://github.com/joaovpg/economize/actions/workflows/ci.yml)

Backend de um sistema pessoal para registrar e catalogar receitas, despesas, transferências e recorrências financeiras.

> [!IMPORTANT]
> O projeto está em desenvolvimento. A entrega atual implementa o núcleo de persistência, cadastro e autenticação JWT de usuários, gestão de contas e categorias, transações simples, consultas com saldo de abertura e transferências simples.

## Estado atual

O repositório contém:

- schema PostgreSQL versionado com Flyway;
- entidades JPA e repositórios Panache para usuários, contas, categorias, recorrências, transações e transferências;
- constraints e uma trigger diferida para proteger invariantes financeiras;
- testes de integração da persistência com banco real via Quarkus Dev Services;
- cadastro público, login com e-mail e senha e emissão de JWT;
- gestão autenticada de contas financeiras, categorias e receitas ou despesas simples planejadas e efetivadas, incluindo alteração, transições e exclusão definitiva;
- consulta mensal unificada de Transações com filtros, impactos assinados, itens de saldo inicial e Saldo de abertura;
- criação, alteração, efetivação, replanejamento e exclusão atômica de Transferências simples, sem Categoria e exibidas por seus dois lados na consulta unificada;
- infraestrutura REST e OpenAPI configurada, além de dependências planejadas para mapeamentos, integrações e métricas.
- erros HTTP padronizados como `application/problem+json` segundo a RFC 9457, com tipos `urn:economize:problem:<codigo>` e detalhes de validação em `errors`.

O próximo marco implementa o Motor de recorrência. Consulte o [roadmap](docs/roadmap.md) para todas as entregas planejadas.

## Modelo financeiro

Cada registro financeiro pertence a um único usuário. O domínio atual é dividido em:

| Módulo | Responsabilidade |
| --- | --- |
| `usuario` | Identidade e isolamento dos dados |
| `conta` | Contas financeiras e saldo inicial |
| `categoria` | Classificação hierárquica de receitas e despesas |
| `recorrencia` | Grupos e segmentos de recorrência |
| `transacao` | Receitas, despesas e lados de transferências |
| `transferencia` | Operação que vincula uma saída e uma entrada |

As regras, convenções físicas e limitações desta entrega estão em [`docs/modelo-financeiro.md`](docs/modelo-financeiro.md).

## Tecnologias

- Java 25 LTS e Quarkus 3;
- Maven Wrapper;
- PostgreSQL, Hibernate ORM with Panache e Flyway;
- Quarkus REST, Jackson e SmallRye OpenAPI;
- MapStruct, REST Client e Micrometer;
- JUnit 5, Quarkus Test e REST Assured.

## Arquitetura

O Economize é um monólito modular organizado por domínio. Os fluxos públicos seguem `Resource -> mapper MapStruct -> caso de uso -> repository Panache`, mantendo regras e transações no módulo responsável, sem pacotes globais por camada.

Cada módulo começa plano e só ganha subpacotes como `http`, `application`, `domain` e `persistence` quando responsabilidades concretas justificarem a divisão. DTOs HTTP permanecem próximos ao adapter, sob `http/dto`, e nunca dependem de entidades JPA ou repositórios.

```text
src/main/java/com/joaovpg/economize/
├── categoria/
├── shared/
├── conta/
├── recorrencia/
├── transacao/
├── transferencia/
└── usuario/
```

Consulte [`AGENTS.md`](AGENTS.md) para as regras de arquitetura e implementação.

## Pré-requisitos

- JDK 25 com `JAVA_HOME` configurado;
- Docker com Docker Compose;
- Git.

Não é necessário instalar Maven separadamente: o repositório inclui o Maven Wrapper.

## Desenvolvimento local

O fluxo local usa o [`docker-compose.yml`](docker-compose.yml) para iniciar o PostgreSQL em `localhost:5432` e o pgAdmin em <http://localhost:5050>. As credenciais declaradas no Compose são destinadas exclusivamente ao desenvolvimento local.

### 1. Configure o ambiente

Copie o arquivo de exemplo para `.env`. O Quarkus carrega automaticamente esse arquivo quando a aplicação é iniciada na raiz do projeto.

Linux e macOS:

```shell
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

O `.env.example` já corresponde ao PostgreSQL definido no Compose:

| Variável | Valor local |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/economize_db` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `rootpassword` |

### 2. Suba o banco

Na raiz do projeto, inicie os contêineres em segundo plano:

```shell
docker compose up -d
```

Confira se `economize_db` e `economize_pgadmin` estão em execução:

```shell
docker compose ps
```

Se o PostgreSQL ainda estiver inicializando, acompanhe seus logs:

```shell
docker compose logs -f db
```

Pressione `Ctrl+C` para sair dos logs sem parar o contêiner.

### 3. Inicie a aplicação

Linux e macOS:

```shell
./mvnw quarkus:dev
```

Windows PowerShell:

```powershell
.\mvnw.cmd quarkus:dev
```

O Flyway aplica as migrations na inicialização e o Hibernate valida o schema. Após a inicialização, estão disponíveis:

| Recurso | Endereço |
| --- | --- |
| API | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/q/swagger-ui> |
| OpenAPI | <http://localhost:8080/q/openapi> |
| Quarkus Dev UI | <http://localhost:8080/q/dev/> |

### Acesse o pgAdmin

Abra <http://localhost:5050> e entre com:

| Campo | Valor |
| --- | --- |
| E-mail | `admin@admin.com` |
| Senha | `admin` |

No primeiro acesso, registre um servidor usando:

| Campo | Valor |
| --- | --- |
| Host | `db` |
| Porta | `5432` |
| Banco de manutenção | `economize_db` |
| Usuário | `postgres` |
| Senha | `rootpassword` |

Use `db`, e não `localhost`, porque o pgAdmin acessa o PostgreSQL pela rede interna do Compose.

### Pare ou reinicie o banco

Para parar e remover os contêineres, preservando os dados no volume `pgdata`:

```shell
docker compose down
```

Para remover também o volume e recriar um banco vazio na próxima inicialização:

```shell
docker compose down -v
```

> [!WARNING]
> O comando `docker compose down -v` apaga definitivamente todos os dados locais armazenados pelo PostgreSQL.

## Testes

Para executar a suíte de testes:

```shell
./mvnw test
```

No Windows:

```powershell
.\mvnw.cmd test
```

A validação usada pela CI é:

```shell
./mvnw verify -B
```

Os testes permanecem isolados do banco local. Eles dependem de um runtime de contêiner disponível para o Quarkus Dev Services iniciar um PostgreSQL temporário.

## Erros HTTP

As respostas de erro seguem o formato [RFC 9457](https://datatracker.ietf.org/doc/html/rfc9457) e usam o media type `application/problem+json`:

```json
{
  "type": "urn:economize:problem:DADOS_INVALIDOS",
  "title": "Dados invalidos",
  "status": 400,
  "detail": "Um ou mais campos sao invalidos",
  "instance": "/api/transacoes",
  "errors": [
    { "field": "dataFinanceira", "detail": "deve ser informada" }
  ]
}
```

O código do problema é identificado pelo sufixo de `type`. O campo `errors` só aparece quando há erros associados a campos. Não são publicados aliases legados como `codigo`, `mensagem` ou `campos`.

## Logs

A API registra cada requisição no console com método, rota template, status, duração, `traceId` e, quando disponível, `usuarioId`. Corpos, query strings, valores de path, credenciais e dados financeiros não são registrados. Em produção, os eventos usam JSON; em desenvolvimento, usam texto legível. Respostas 4xx são `WARN`, erros 5xx são `ERROR` com stack trace e requisições acima de `LOG_LIMITE_REQUISICAO_LENTA` (padrão `1000` ms) recebem um `WARN` adicional.

## Configuração

Nos profiles `dev` e `prod`, configure o datasource pelas variáveis abaixo. O profile `test` permanece isolado e usa um PostgreSQL temporário fornecido pelo Dev Services.

| Variável | Descrição | Exemplo |
| --- | --- | --- |
| `DB_URL` | URL JDBC do PostgreSQL | `jdbc:postgresql://localhost:5432/economize_db` |
| `DB_USERNAME` | Usuário do banco | `postgres` |
| `DB_PASSWORD` | Senha do banco | Não versionar este valor |
| `JWT_CHAVE_PUBLICA` | Localização da chave pública RSA usada para validar tokens | `/run/secrets/jwt-public.pem` |
| `JWT_CHAVE_PRIVADA` | Localização da chave privada RSA usada para assinar tokens | `/run/secrets/jwt-private.pem` |
| `JWT_EXPIRACAO_SEGUNDOS` | Validade do token de acesso | `900` |
| `COOKIE_SAME_SITE` | Política SameSite dos cookies de sessão e CSRF | `Lax` localmente; `None` na homologação; `Strict` quando frontend e API forem same-site |
| `COOKIE_SECURE` | Exige HTTPS nos cookies | `true` na homologação e produção |
| `CORS_ORIGINS` | Origens explícitas autorizadas pelo CORS | `http://localhost:3000` |

O login e o cadastro criam os cookies HttpOnly `economize_token` e legível `economize_csrf`; o JWT não é devolvido no corpo da resposta. Requisições do frontend devem usar credenciais (`credentials: 'include'`) e enviar o valor de `economize_csrf` no header `X-CSRF-Token` em operações que alteram dados. O Swagger pode ser autenticado executando o endpoint de login pelo próprio `Try it out`.

O Flyway aplica as migrations na inicialização e o Hibernate apenas valida o schema. Em execução local, o Quarkus carrega automaticamente o arquivo `.env` localizado na raiz do projeto. O `.env` é ignorado pelo Git. Em outros ambientes, forneça as variáveis e as chaves RSA pela plataforma de execução.

## Build JVM

Linux e macOS:

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Windows PowerShell:

```powershell
.\mvnw.cmd package
java -jar target/quarkus-app/quarkus-run.jar
```

## Build nativo

O build nativo é opcional e requer uma distribuição GraalVM/Mandrel compatível. Para produzir o executável usando um contêiner:

```shell
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Consulte a [documentação de build nativo do Quarkus](https://quarkus.io/guides/building-native-image) para os requisitos adicionais.

## Documentação

- [Modelo financeiro](docs/modelo-financeiro.md)
- [Roadmap](docs/roadmap.md)
- [Instruções para agentes](AGENTS.md)

Este repositório ainda não declara uma licença de uso.
