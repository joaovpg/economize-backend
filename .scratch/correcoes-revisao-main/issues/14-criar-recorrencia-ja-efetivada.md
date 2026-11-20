# 14: Criar Recorrência já efetivada

**What to build:** a issue 05 do roadmap diz: "Ao criar uma repetição já `EFETIVADA`, somente a primeira ocorrência é persistida". Hoje toda Recorrência nasce planejada, e o roadmap registra isso como entrega parcial. É preciso decidir se a capacidade entra no MVP e, se entrar, permitir que o Usuário crie uma Recorrência ou um Parcelamento com a primeira ocorrência já efetivada.

**Blocked by:** None (can start immediately)

**Status:** needs-triage

- [ ] Decidido se a capacidade entra no MVP, com a decisão registrada no roadmap.
- [ ] Se entrar: a criação aceita `situacao`; com `EFETIVADA`, somente a primeira ocorrência é persistida, com `efetivadoEm`, e as demais continuam virtuais.
- [ ] Se entrar: uma Data financeira futura no fuso do Usuário é rejeitada para `EFETIVADA`.
- [ ] Se entrar: testes HTTP cobrem Recorrência comum e Parcelamento; `mvnw verify -B` passa.
