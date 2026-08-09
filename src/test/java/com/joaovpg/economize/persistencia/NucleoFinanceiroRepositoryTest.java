package com.joaovpg.economize.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joaovpg.economize.categoria.Categoria;
import com.joaovpg.economize.categoria.CategoriaRepository;
import com.joaovpg.economize.conta.ContaFinanceira;
import com.joaovpg.economize.conta.ContaFinanceiraRepository;
import com.joaovpg.economize.recorrencia.GrupoRecorrencia;
import com.joaovpg.economize.recorrencia.GrupoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.SegmentoRecorrencia;
import com.joaovpg.economize.recorrencia.SegmentoRecorrenciaRepository;
import com.joaovpg.economize.recorrencia.enums.StatusRecorrencia;
import com.joaovpg.economize.transacao.SituacaoTransacao;
import com.joaovpg.economize.transacao.TipoTransacao;
import com.joaovpg.economize.transacao.Transacao;
import com.joaovpg.economize.transacao.TransacaoRepository;
import com.joaovpg.economize.transferencia.SituacaoTransferencia;
import com.joaovpg.economize.transferencia.Transferencia;
import com.joaovpg.economize.transferencia.TransferenciaRepository;
import com.joaovpg.economize.usuario.Usuario;
import com.joaovpg.economize.usuario.UsuarioRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NucleoFinanceiroRepositoryTest {
  @Inject UsuarioRepository usuarioRepository;
  @Inject ContaFinanceiraRepository contaRepository;
  @Inject CategoriaRepository categoriaRepository;
  @Inject GrupoRecorrenciaRepository grupoRepository;
  @Inject SegmentoRecorrenciaRepository segmentoRepository;
  @Inject TransacaoRepository transacaoRepository;
  @Inject TransferenciaRepository transferenciaRepository;
  @Inject EntityManager entityManager;

  @Test
  @TestTransaction
  void persisteUsuarioContaECategoriasHierarquicas() {
    var usuario = novoUsuario("pessoa@example.com");
    usuarioRepository.persist(usuario);

    var conta = novaConta(usuario, "Conta principal");
    contaRepository.persist(conta);

    var categoriaPai = novaCategoria(usuario, "Moradia", null);
    categoriaRepository.persist(categoriaPai);
    var categoriaFilha = novaCategoria(usuario, "Moveis", categoriaPai);
    categoriaRepository.persist(categoriaFilha);
    categoriaRepository.flush();

    assertNotNull(usuario.getId());
    assertEquals(new BigDecimal("-50.0000"), conta.getSaldoInicial());
    assertTrue(conta.isAtivo());
    conta.setAtivo(false);
    contaRepository.flush();
    assertFalse(conta.isAtivo());
    assertEquals(categoriaPai.getId(), categoriaFilha.getCategoriaPai().getId());
  }

  @Test
  @TestTransaction
  void representaParcelamentoSegmentadoComExcecao() {
    var usuario = novoUsuario("parcelas@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Carteira");
    contaRepository.persist(conta);
    var grupo = novoGrupo(usuario, "Movel em 12 parcelas");
    grupoRepository.persist(grupo);

    var segmentoA =
        novoSegmento(grupo, conta, "300.00", "FREQ=MONTHLY;COUNT=2", 2, LocalDate.of(2026, 1, 10));
    var segmentoB =
        novoSegmento(grupo, conta, "300.00", "FREQ=MONTHLY;COUNT=2", 2, LocalDate.of(2026, 4, 10));
    var segmentoC =
        novoSegmento(grupo, conta, "350.00", "FREQ=MONTHLY;COUNT=7", 7, LocalDate.of(2026, 6, 10));
    segmentoRepository.persist(segmentoA, segmentoB, segmentoC);

    var excecao =
        novaTransacao(usuario, conta, TipoTransacao.DESPESA, "60.00", LocalDate.of(2026, 3, 10));
    excecao.setGrupoRecorrencia(grupo);
    excecao.setExcecaoRecorrencia(true);
    transacaoRepository.persist(excecao);
    transacaoRepository.flush();

    assertEquals(3, segmentoRepository.count("grupo", grupo));
    assertEquals(grupo.getId(), excecao.getGrupoRecorrencia().getId());
    assertNull(excecao.getSegmentoRecorrencia());
    assertTrue(excecao.isExcecaoRecorrencia());
    assertEquals(7, segmentoC.getTotalOcorrencias());
  }

  @Test
  @TestTransaction
  void persisteTransferenciaComDoisLancamentos() {
    var usuario = novoUsuario("transferencia@example.com");
    usuarioRepository.persist(usuario);
    var origem = novaConta(usuario, "Origem");
    var destino = novaConta(usuario, "Destino");
    contaRepository.persist(origem, destino);
    var data = LocalDate.of(2026, 7, 24);
    var saida =
        novoLadoTransferencia(
            usuario, origem, TipoTransacao.DESPESA, "125.00", data, "Reserva mensal");
    var entrada =
        novoLadoTransferencia(
            usuario, destino, TipoTransacao.RECEITA, "125.00", data, "Reserva mensal");
    transacaoRepository.persist(saida, entrada);

    var transferencia = new Transferencia();
    transferencia.setUsuario(usuario);
    transferencia.setContaOrigem(origem);
    transferencia.setContaDestino(destino);
    transferencia.setTransacaoSaida(saida);
    transferencia.setTransacaoEntrada(entrada);
    transferencia.setSituacao(SituacaoTransferencia.PLANEJADA);
    transferencia.setDescricao("Reserva mensal");
    transferencia.setValor(new BigDecimal("125.00"));
    transferencia.setDataFinanceira(data);
    transferenciaRepository.persist(transferencia);
    transferenciaRepository.flush();
    validarConstraintsDiferidas();

    assertNotNull(transferencia.getId());
    assertEquals(saida.getId(), transferencia.getTransacaoSaida().getId());
    assertEquals(entrada.getId(), transferencia.getTransacaoEntrada().getId());
    assertEquals(SituacaoTransferencia.PLANEJADA, transferencia.getSituacao());
  }

  @Test
  @TestTransaction
  void permiteAtualizarTransferenciaExistenteComContaInativa() {
    var usuario = novoUsuario("transferencia-inativa@example.com");
    usuarioRepository.persist(usuario);
    var origem = novaConta(usuario, "Origem inativada");
    var destino = novaConta(usuario, "Destino mantido");
    contaRepository.persist(origem, destino);
    var data = LocalDate.of(2026, 7, 24);
    var saida =
        novoLadoTransferencia(
            usuario, origem, TipoTransacao.DESPESA, "125.00", data, "Reserva mensal");
    var entrada =
        novoLadoTransferencia(
            usuario, destino, TipoTransacao.RECEITA, "125.00", data, "Reserva mensal");
    transacaoRepository.persist(saida, entrada);
    var transferencia = new Transferencia();
    transferencia.setUsuario(usuario);
    transferencia.setContaOrigem(origem);
    transferencia.setContaDestino(destino);
    transferencia.setTransacaoSaida(saida);
    transferencia.setTransacaoEntrada(entrada);
    transferencia.setSituacao(SituacaoTransferencia.PLANEJADA);
    transferencia.setDescricao("Reserva mensal");
    transferencia.setValor(new BigDecimal("125.00"));
    transferencia.setDataFinanceira(data);
    transferenciaRepository.persist(transferencia);
    transferenciaRepository.flush();
    validarConstraintsDiferidas();

    origem.setAtivo(false);
    transferencia.setDescricao("Reserva mensal corrigida");
    saida.setDescricao("Reserva mensal corrigida");
    entrada.setDescricao("Reserva mensal corrigida");
    transferenciaRepository.flush();

    assertEquals("Reserva mensal corrigida", transferencia.getDescricao());
  }

  @Test
  @TestTransaction
  void rejeitaEmailDuplicado() {
    usuarioRepository.persist(novoUsuario("duplicado@example.com"));
    usuarioRepository.flush();
    usuarioRepository.persist(novoUsuario("duplicado@example.com"));

    assertThrows(PersistenceException.class, usuarioRepository::flush);
  }

  @Test
  @TestTransaction
  void rejeitaTransacaoComValorZero() {
    var usuario = novoUsuario("valor@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Conta");
    contaRepository.persist(conta);
    var transacao = novaTransacao(usuario, conta, TipoTransacao.DESPESA, "0.00", LocalDate.now());
    transacaoRepository.persist(transacao);

    assertThrows(PersistenceException.class, transacaoRepository::flush);
  }

  @Test
  @TestTransaction
  void rejeitaContaDeOutroUsuarioNaTransacao() {
    var proprietario = novoUsuario("proprietario@example.com");
    var terceiro = novoUsuario("terceiro@example.com");
    usuarioRepository.persist(proprietario, terceiro);
    var conta = novaConta(proprietario, "Conta privada");
    contaRepository.persist(conta);
    var transacao = novaTransacao(terceiro, conta, TipoTransacao.DESPESA, "10.00", LocalDate.now());
    transacaoRepository.persist(transacao);

    assertThrows(PersistenceException.class, transacaoRepository::flush);
  }

  @Test
  @TestTransaction
  void bloqueiaDadosIniciaisAoPersistirPrimeiraTransacao() {
    var usuario = novoUsuario("historico@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Conta com historico");
    contaRepository.persist(conta);
    transacaoRepository.persist(
        novaTransacao(usuario, conta, TipoTransacao.DESPESA, "10.00", LocalDate.of(2026, 1, 1)));
    transacaoRepository.flush();

    var bloqueados =
        (Boolean)
            entityManager
                .createNativeQuery(
                    """
                    SELECT BOL_DADOS_INICIAIS_BLOQUEADOS
                    FROM TB002_CONTA_FINANCEIRA
                    WHERE ID_REGISTRO = :contaId
                    """)
                .setParameter("contaId", conta.getId())
                .getSingleResult();

    assertTrue(bloqueados);
  }

  @Test
  @TestTransaction
  void mantemDadosIniciaisBloqueadosDepoisDeExcluirTransacao() {
    var usuario = novoUsuario("historico-excluido@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Conta com historico excluido");
    contaRepository.persist(conta);
    var transacao =
        novaTransacao(usuario, conta, TipoTransacao.DESPESA, "10.00", LocalDate.of(2026, 1, 1));
    transacaoRepository.persist(transacao);
    transacaoRepository.flush();
    transacaoRepository.delete(transacao);
    transacaoRepository.flush();

    var bloqueados =
        (Boolean)
            entityManager
                .createNativeQuery(
                    """
                    SELECT BOL_DADOS_INICIAIS_BLOQUEADOS
                    FROM TB002_CONTA_FINANCEIRA
                    WHERE ID_REGISTRO = :contaId
                    """)
                .setParameter("contaId", conta.getId())
                .getSingleResult();

    assertTrue(bloqueados);
  }

  @Test
  @TestTransaction
  void impedeAlterarDadosIniciaisBloqueadosDiretamenteNoBanco() {
    var usuario = novoUsuario("bloqueio-banco@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Conta bloqueada");
    contaRepository.persist(conta);
    transacaoRepository.persist(
        novaTransacao(usuario, conta, TipoTransacao.RECEITA, "10.00", LocalDate.of(2026, 1, 1)));
    transacaoRepository.flush();

    assertThrows(
        PersistenceException.class,
        () ->
            entityManager
                .createNativeQuery(
                    """
                    UPDATE TB002_CONTA_FINANCEIRA
                    SET DEC_SALDO_INICIAL = 100
                    WHERE ID_REGISTRO = :contaId
                    """)
                .setParameter("contaId", conta.getId())
                .executeUpdate());
  }

  @Test
  @TestTransaction
  void bloqueiaDadosIniciaisAoPersistirPrimeiroSegmento() {
    var usuario = novoUsuario("segmento-historico@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Conta recorrente");
    contaRepository.persist(conta);
    var grupo = novoGrupo(usuario, "Receita mensal");
    grupoRepository.persist(grupo);
    segmentoRepository.persist(
        novoSegmento(grupo, conta, "100.00", "FREQ=MONTHLY", 12, LocalDate.of(2026, 1, 1)));
    segmentoRepository.flush();

    var bloqueados =
        (Boolean)
            entityManager
                .createNativeQuery(
                    """
                    SELECT BOL_DADOS_INICIAIS_BLOQUEADOS
                    FROM TB002_CONTA_FINANCEIRA
                    WHERE ID_REGISTRO = :contaId
                    """)
                .setParameter("contaId", conta.getId())
                .getSingleResult();

    assertTrue(bloqueados);
  }

  @Test
  @TestTransaction
  void rejeitaOperacaoAnteriorADataDoSaldoInicial() {
    var usuario = novoUsuario("data-inicial@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Conta com data inicial");
    contaRepository.persist(conta);
    transacaoRepository.persist(
        novaTransacao(usuario, conta, TipoTransacao.DESPESA, "10.00", LocalDate.of(2025, 12, 31)));

    assertThrows(PersistenceException.class, transacaoRepository::flush);
  }

  @Test
  @TestTransaction
  void rejeitaNomeDeContaDuplicadoSemDiferenciarCaixa() {
    var usuario = novoUsuario("nome-conta@example.com");
    usuarioRepository.persist(usuario);
    contaRepository.persist(novaConta(usuario, "Reserva"));
    contaRepository.flush();
    contaRepository.persist(novaConta(usuario, "reserva"));

    assertThrows(PersistenceException.class, contaRepository::flush);
  }

  @Test
  @TestTransaction
  void rejeitaContaComMoedaForaDoMvp() {
    var usuario = novoUsuario("moeda-conta@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Conta em dolar");
    conta.setMoeda("USD");
    contaRepository.persist(conta);

    assertThrows(PersistenceException.class, contaRepository::flush);
  }

  @Test
  @TestTransaction
  void rejeitaNovaTransacaoEmContaInativa() {
    var usuario = novoUsuario("conta-inativa@example.com");
    usuarioRepository.persist(usuario);
    var conta = novaConta(usuario, "Conta inativa");
    conta.setAtivo(false);
    contaRepository.persist(conta);
    transacaoRepository.persist(
        novaTransacao(usuario, conta, TipoTransacao.DESPESA, "10.00", LocalDate.of(2026, 1, 1)));

    assertThrows(PersistenceException.class, transacaoRepository::flush);
  }

  @Test
  @TestTransaction
  void rejeitaTransferenciaIncompativelComLancamentos() {
    var usuario = novoUsuario("inconsistente@example.com");
    usuarioRepository.persist(usuario);
    var origem = novaConta(usuario, "Origem");
    var destino = novaConta(usuario, "Destino");
    contaRepository.persist(origem, destino);
    var data = LocalDate.of(2026, 7, 24);
    var saida =
        novoLadoTransferencia(
            usuario, origem, TipoTransacao.DESPESA, "100.00", data, "Valor divergente");
    var entrada =
        novoLadoTransferencia(
            usuario, destino, TipoTransacao.RECEITA, "100.00", data, "Valor divergente");
    transacaoRepository.persist(saida, entrada);

    var transferencia = new Transferencia();
    transferencia.setUsuario(usuario);
    transferencia.setContaOrigem(origem);
    transferencia.setContaDestino(destino);
    transferencia.setTransacaoSaida(saida);
    transferencia.setTransacaoEntrada(entrada);
    transferencia.setSituacao(SituacaoTransferencia.PLANEJADA);
    transferencia.setDescricao("Valor divergente");
    transferencia.setValor(new BigDecimal("200.00"));
    transferencia.setDataFinanceira(data);
    transferenciaRepository.persist(transferencia);
    transferenciaRepository.flush();

    assertThrows(PersistenceException.class, this::validarConstraintsDiferidas);
  }

  private void validarConstraintsDiferidas() {
    entityManager
        .createNativeQuery("SET CONSTRAINTS TG007_01_VALIDAR_TRANSFERENCIA IMMEDIATE")
        .executeUpdate();
  }

  private Usuario novoUsuario(String email) {
    var usuario = new Usuario();
    usuario.setNome("Usuario de teste");
    usuario.setEmail(email);
    usuario.setSenhaHash("$argon2id$hash-de-teste");
    usuario.setTimezone("America/Sao_Paulo");
    usuario.setAtivo(true);
    return usuario;
  }

  private ContaFinanceira novaConta(Usuario usuario, String nome) {
    var conta = new ContaFinanceira();
    conta.setUsuario(usuario);
    conta.setNome(nome);
    conta.setMoeda("BRL");
    conta.setSaldoInicial(new BigDecimal("-50.0000"));
    conta.setDataSaldoInicial(LocalDate.of(2026, 1, 1));
    return conta;
  }

  private Categoria novaCategoria(Usuario usuario, String nome, Categoria pai) {
    var categoria = new Categoria();
    categoria.setUsuario(usuario);
    categoria.setNome(nome);
    categoria.setCor("#3355AA");
    categoria.setCategoriaPai(pai);
    return categoria;
  }

  private GrupoRecorrencia novoGrupo(Usuario usuario, String descricao) {
    var grupo = new GrupoRecorrencia();
    grupo.setUsuario(usuario);
    grupo.setDescricao(descricao);
    grupo.setStatus(StatusRecorrencia.ATIVO);
    return grupo;
  }

  private SegmentoRecorrencia novoSegmento(
      GrupoRecorrencia grupo,
      ContaFinanceira conta,
      String valor,
      String rrule,
      int total,
      LocalDate inicio) {
    var segmento = new SegmentoRecorrencia();
    segmento.setUsuario(grupo.getUsuario());
    segmento.setGrupo(grupo);
    segmento.setConta(conta);
    segmento.setTipo(TipoTransacao.DESPESA);
    segmento.setDescricao("Parcela do movel");
    segmento.setValor(new BigDecimal(valor));
    segmento.setInicio(inicio);
    segmento.setRrule(rrule);
    segmento.setTotalOcorrencias(total);
    segmento.setStatus(StatusRecorrencia.ATIVO);
    return segmento;
  }

  private Transacao novaTransacao(
      Usuario usuario,
      ContaFinanceira conta,
      TipoTransacao tipo,
      String valor,
      LocalDate vencimento) {
    var transacao = new Transacao();
    transacao.setUsuario(usuario);
    transacao.setConta(conta);
    transacao.setTipo(tipo);
    transacao.setSituacao(SituacaoTransacao.PLANEJADA);
    transacao.setDescricao("Lancamento de teste");
    transacao.setValor(new BigDecimal(valor));
    transacao.setDataFinanceira(vencimento);
    return transacao;
  }

  private Transacao novoLadoTransferencia(
      Usuario usuario,
      ContaFinanceira conta,
      TipoTransacao tipo,
      String valor,
      LocalDate data,
      String descricao) {
    var transacao = novaTransacao(usuario, conta, tipo, valor, data);
    transacao.setDescricao(descricao);
    return transacao;
  }
}
