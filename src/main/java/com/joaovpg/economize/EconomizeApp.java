package com.joaovpg.economize;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@OpenAPIDefinition(
    info =
        @Info(
            title = "Economize backend",
            description =
                "API para gestão financeira pessoal. A autenticação usa cookie HttpOnly e "
                    + "proteção CSRF para requisições mutáveis.",
            version = "1.0.0"),
    tags = {
      @Tag(name = "Autenticação", description = "Cadastro, login e encerramento de sessão."),
      @Tag(name = "Contas", description = "Contas financeiras e seus saldos iniciais."),
      @Tag(name = "Categorias", description = "Categorias financeiras e hierarquia de categorias."),
      @Tag(
          name = "Transações",
          description = "Receitas, despesas e consulta do extrato consolidado."),
      @Tag(
          name = "Transferências",
          description = "Movimentações entre duas contas na mesma moeda."),
      @Tag(
          name = "Recorrências",
          description =
              "Recorrências, parcelamentos e operações sobre ocorrências virtuais ou efetivadas.")
    })
@ApplicationPath("/api")
@QuarkusMain(name = "api")
public class EconomizeApp extends Application implements QuarkusApplication {

  public static void main(String... args) {
    Quarkus.run(EconomizeApp.class, args);
  }

  @Override
  public int run(String... args) {
    Quarkus.waitForExit();
    return 0;
  }
}
