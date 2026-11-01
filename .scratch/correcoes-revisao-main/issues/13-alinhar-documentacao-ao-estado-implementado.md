# 13: Alinhar a documentação ao estado implementado

**What to build:** quem lê a documentação, seja pessoa ou agente, encontra o que realmente existe hoje. A orientação para agentes e o README deixam de dizer que Transferências e Recorrências têm "principalmente persistência" e que o motor de recorrência é "o próximo marco". O glossário passa a definir o número da parcela como estável durante todo o plano, conforme a decisão da issue 06 do roadmap e o comportamento do código. A ADR de saldo passa a refletir que as consultas devolvem valor com sinal. As issues locais substituídas ou já entregues recebem nota ou status corrigido.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] AGENTS.md e README listam Transferências, Recorrências e Parcelamentos como fluxos públicos existentes e descrevem corretamente a janela de consulta de 1 a 12 meses.
- [ ] CONTEXT.md define "Número da parcela" e "Total de parcelas" de forma coerente com a numeração estável entre Segmentos.
- [ ] A ADR 0004 recebe nota de atualização sobre valor com sinal, sem reescrever a decisão original.
- [ ] As issues 03 e 04 do roadmap registram o que as substituiu, e a issue 03 de Transações simples tem status coerente com a entrega.
- [ ] Os comandos e caminhos citados foram validados; não há mudança de código.
