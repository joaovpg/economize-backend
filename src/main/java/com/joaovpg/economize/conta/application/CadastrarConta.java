package com.joaovpg.economize.conta.application;

import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.usuario.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@ApplicationScoped
public class CadastrarConta {
  private final UsuarioRepository usuarioRepository;
  private final ContaFinanceiraRepository contaRepository;

  public CadastrarConta(
      UsuarioRepository usuarioRepository, ContaFinanceiraRepository contaRepository) {
    this.usuarioRepository = usuarioRepository;
    this.contaRepository = contaRepository;
  }

  @Transactional
  public ContaResultado executar(Comando comando) {
    var nome = ContaValidacao.nome(comando.nome());
    var moeda = ContaValidacao.moeda(comando.moeda());
    var saldoInicial = ContaValidacao.saldoInicial(comando.saldoInicial());
    var usuario =
        usuarioRepository
            .buscarAtivo(comando.usuarioId())
            .orElseThrow(CadastrarConta::naoEncontrada);

    var dataSaldoInicial =
        ContaValidacao.dataSaldoInicial(
            comando.dataSaldoInicial(), LocalDate.now(ZoneId.of(usuario.getTimezone())));
    ContaValidacao.nomeDisponivel(contaRepository, comando.usuarioId(), nome);

    var conta = new ContaFinanceira();
    conta.setUsuario(usuario);
    conta.setNome(nome);
    conta.setMoeda(moeda);
    conta.setSaldoInicial(saldoInicial);
    conta.setDataSaldoInicial(dataSaldoInicial);
    contaRepository.persist(conta);
    ContaValidacao.flush(contaRepository);
    return ContaValidacao.resultado(conta);
  }

  static RecursoNaoEncontradoException naoEncontrada() {
    return new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Conta nao encontrada");
  }

  public record Comando(
      UUID usuarioId,
      String nome,
      String moeda,
      BigDecimal saldoInicial,
      LocalDate dataSaldoInicial) {}
}
