package com.joaovpg.economize.shared.http.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Proxy;
import java.net.URI;
import org.junit.jupiter.api.Test;

/** Perda de corrida de versão em gravação vira 409 VERSAO_DESATUALIZADA, nunca 500. */
class VersaoDesatualizadaMapperTest {

  @Test
  void perdaDeVersaoNaGravacaoRespondeConflitoSemDadosFinanceiros() throws Exception {
    var mapper = new VersaoDesatualizadaMapper();
    var uriInfo =
        (UriInfo)
            Proxy.newProxyInstance(
                UriInfo.class.getClassLoader(),
                new Class<?>[] {UriInfo.class},
                (proxy, metodo, argumentos) ->
                    "getRequestUri".equals(metodo.getName())
                        ? URI.create("/api/transacoes/chave")
                        : null);
    var campo = VersaoDesatualizadaMapper.class.getDeclaredField("uriInfo");
    campo.setAccessible(true);
    campo.set(mapper, uriInfo);

    var resposta = mapper.toResponse(new OptimisticLockException("conflito de versao"));

    assertEquals(409, resposta.getStatus());
    var problema = (HttpProblem) resposta.getEntity();
    assertNotNull(problema);
    assertEquals(URI.create("urn:economize:problem:VERSAO_DESATUALIZADA"), problema.getType());
    assertEquals(409, problema.getStatusCode());
    assertEquals("O recurso foi alterado por outra operacao", problema.getDetail());
  }
}
