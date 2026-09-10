# ADR 0011: Autenticacao por cookie e protecao CSRF

## Status

Aceita

## Contexto

A API emite JWTs assinados localmente e os clientes passaram a precisar de uma sessao de navegador. O frontend da homologacao roda em `localhost` enquanto a API esta em outro dominio, portanto o cookie precisa aceitar chamadas cross-site. O uso de cookies tambem cria risco de CSRF em operacoes mutaveis.

## Decisao

A autenticacao de navegador usa o cookie host-only `economize_token`, com `HttpOnly`, `Path=/api`, validade igual a `JWT_EXPIRACAO_SEGUNDOS` e politica `SameSite` configuravel por `COOKIE_SAME_SITE`. O ambiente local usa `Lax`; a homologacao cross-site usa `None` com `COOKIE_SECURE=true`; ambientes same-site podem usar `Strict`.

O mecanismo de autenticacao Quarkus le exclusivamente o cookie e delega a validacao ao provedor SmallRye JWT existente. O header `Authorization` nao e aceito.

O login e o cadastro tambem criam `economize_csrf`, que nao e HttpOnly e usa `Path=/`, para que clientes JavaScript da mesma origem, incluindo o Swagger UI em `/q/swagger-ui`, consigam le-lo. Operacoes mutaveis exigem que seu valor seja repetido no header `X-CSRF-Token`. O logout expira os cookies de sessao.

Para evitar conflito com versoes anteriores, login, cadastro e logout tambem expiram explicitamente um eventual `economize_csrf` legado com `Path=/api`.

O CORS aceita credenciais e somente origens explicitamente configuradas em `CORS_ORIGINS`.

O Swagger usa o login por `Try it out` para estabelecer a sessao no navegador. O `request-interceptor` le `economize_csrf` de `document.cookie` e o envia automaticamente em `X-CSRF-Token`. `show-mutated-request` fica habilitado para que o curl exibido pelo Swagger mostre o header adicionado pelo interceptor.

## Consequencias

- O JWT deixa de ser exposto no corpo das respostas HTTP.
- O frontend precisa usar credenciais e enviar o header CSRF em escritas.
- Clientes que usavam Bearer precisam migrar para cookies.
- O Swagger nao depende do botao `Authorize` para autenticar ou propagar o token CSRF.
- O token CSRF pode ser lido por JavaScript em qualquer caminho da mesma origem, o que e esperado no padrao double-submit cookie; o JWT continua protegido por `HttpOnly`.
