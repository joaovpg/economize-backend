package com.joaovpg.economize.conta.application;

import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@ApplicationScoped
public class EditarConta {
  private final ContaFinanceiraRepository contaRepository;

  public EditarConta(ContaFinanceiraRepository contaRepository) {
    this.contaRepository = contaRepository;
  }

  @Transactional
  public ContaResultado executar(Comando comando) {
    var conta =
        contaRepository
            .buscarDoUsuarioParaEdicao(comando.contaId(), comando.usuarioId())
            .orElseThrow(CadastrarConta::naoEncontrada);

    if (comando.ativo() == null) {
      throw new RegraNegocioException("ATIVO_CONTA_INVALIDO", "Flag ativo da conta invalida");
    }

    var nome = ContaValidation.nome(comando.nome());
    var moeda = ContaValidation.moeda(comando.moeda());
    var saldoInicial = ContaValidation.saldoInicial(comando.saldoInicial());
    var dataSaldoInicial =
        ContaValidation.dataSaldoInicial(
            comando.dataSaldoInicial(), LocalDate.now(ZoneId.of(conta.getUsuario().getTimezone())));
    ContaValidation.nomeDisponivel(contaRepository, comando.usuarioId(), nome, conta.getId());
    ContaValidation.dadosIniciaisEditaveis(conta, moeda, saldoInicial, dataSaldoInicial);

    conta.setNome(nome);
    conta.setMoeda(moeda);
    conta.setSaldoInicial(saldoInicial);
    conta.setDataSaldoInicial(dataSaldoInicial);
    conta.setAtivo(comando.ativo());
    ContaValidation.flush(contaRepository);
    return ContaValidation.resultado(conta);
  }

  public record Comando(
      UUID usuarioId,
      UUID contaId,
      String nome,
      String moeda,
      BigDecimal saldoInicial,
      LocalDate dataSaldoInicial,
      Boolean ativo) {}
}
