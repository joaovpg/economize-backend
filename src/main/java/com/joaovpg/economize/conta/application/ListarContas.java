package com.joaovpg.economize.conta.application;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ListarContas {
  private final ContaFinanceiraRepository contaRepository;

  public ListarContas(ContaFinanceiraRepository contaRepository) {
    this.contaRepository = contaRepository;
  }

  @Transactional
  public List<ContaResultado> executar(UUID usuarioId) {
    return resultados(contaRepository.listarDoUsuario(usuarioId));
  }

  @Transactional
  public List<ContaResultado> executar(UUID usuarioId, Boolean ativo) {
    return resultados(contaRepository.listarDoUsuario(usuarioId, ativo));
  }

  private List<ContaResultado> resultados(List<ContaFinanceira> contas) {
    return contas.stream().map(ContaValidation::resultado).toList();
  }
}
