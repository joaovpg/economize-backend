package com.joaovpg.economize.transacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentidadeEdicaoTransacaoTest {
  @Test
  void preservaIdentidadeEmChaveSeguraParaUrl() {
    var id = UUID.randomUUID();
    var chave = IdentidadeEdicaoTransacao.chave(id);
    assertTrue(chave.matches("[A-Za-z0-9_-]+"));
    assertEquals(id, IdentidadeEdicaoTransacao.identificar(chave));
    assertNotEquals(IdentidadeEdicaoTransacao.versao(0), IdentidadeEdicaoTransacao.versao(1));
  }

  @Test
  void rejeitaChaveInvalida() {
    assertThrows(
        RecursoNaoEncontradoException.class,
        () -> IdentidadeEdicaoTransacao.identificar("invalida"));
  }
}
