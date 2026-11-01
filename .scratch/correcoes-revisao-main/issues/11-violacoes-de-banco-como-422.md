# 11: Violações de trigger e constraint respondem 422 RFC 9457

**What to build:** hoje, quando uma escrita viola uma invariante protegida por trigger ou constraint do PostgreSQL fora de Conta financeira e Categoria, a API responde `500` genérico. Passa a responder `422` com problem detail RFC 9457 (ADR 0007) e um código estável. O log não pode registrar valores financeiros nem detalhes de chave vindos do PostgreSQL (ADR 0008).

**Blocked by:** 10 — Descrição não vazia garantida também no banco

**Status:** ready-for-agent

- [ ] Violações das triggers financeiras e das constraints de Transações, Transferências e Recorrências chegam ao cliente como `422` com código estável por invariante.
- [ ] Os códigos são os mesmos que a validação de aplicação usa para a mesma invariante, quando ela existe.
- [ ] Violações sem mapeamento conhecido continuam respondendo `500` e são registradas uma única vez.
- [ ] Os logs não contêm valores, descrições nem detalhes de chave vindos do PostgreSQL.
- [ ] Testes forçam ao menos uma violação de trigger e uma de constraint por módulo, passando pela camada de aplicação.
- [ ] `mvnw verify -B` passa.
