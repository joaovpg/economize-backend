# ADR 0011: Autenticacao por cookie e protecao CSRF

## Status

Aceita

## Contexto

A API emite JWTs assinados localmente e os clientes passaram a precisar de uma sessao de navegador. O frontend da homologacao roda em `localhost` enquanto a API esta em outro dominio, portanto o cookie precisa aceitar chamadas cross-site. O uso de cookies tambem cria risco de CSRF em operacoes mutaveis.

## Decisao

A autenticacao de navegador usa o cookie host-only `economize_token`, com `HttpOnly`, `Path=/api`, validade igual a `JWT_EXPIRACAO_SEGUNDOS` e politica `SameSite` configuravel por `COOKIE_SAME_SITE`. O ambiente local usa `Lax`; a homologacao cross-site usa `None` com `COOKIE_SECURE=true`; ambientes same-site podem usar `Strict`.

O mecanismo de autenticacao Quarkus le exclusivamente o cookie e delega a validacao ao provedor SmallRye JWT existente. O header `Authorization` nao e aceito.

O login e o cadastro tambem criam `economize_csrf`, que nao e HttpOnly e usa `Path=/api`. Em operacoes mutaveis iniciadas de outro origin, seu valor deve ser repetido no header `X-CSRF-Token`.

Requisicoes mutaveis que o navegador identifica como `Sec-Fetch-Site: same-origin` nao exigem o token CSRF adicional. `Sec-Fetch-Site` e um Fetch Metadata Request Header controlado pelo navegador, portanto nao pode ser definido ou alterado por JavaScript da pagina. Requisicoes sem esse sinal, inclusive clientes HTTP e navegadores legados, continuam seguindo a validacao por cookie + header CSRF.

O CORS aceita credenciais e somente origens explicitamente configuradas em `CORS_ORIGINS`.

O Swagger usa o login por `Try it out` para estabelecer a sessao no navegador. Como o Swagger UI e servido no mesmo origin da API, suas chamadas recebem `Sec-Fetch-Site: same-origin` do navegador e nao precisam copiar o token CSRF nem usar o botao `Authorize`.

## Consequencias

- O JWT deixa de ser exposto no corpo das respostas HTTP.
- O frontend cross-site precisa usar credenciais e enviar o header CSRF em escritas.
- Clientes que usavam Bearer precisam migrar para cookies.
- O Swagger permanece autenticado pelo cookie `HttpOnly` e nao precisa de logica JavaScript especifica para CSRF.
- Requisicoes cross-site continuam protegidas pelo mecanismo double-submit cookie.
