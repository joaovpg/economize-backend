package com.joaovpg.economize.shared.http.erro;

import com.joaovpg.economize.shared.exception.AutenticacaoException;
import com.joaovpg.economize.shared.exception.RecursoNaoEncontradoException;
import com.joaovpg.economize.shared.exception.RegraNegocioException;
import com.joaovpg.economize.shared.exception.ValidacaoException;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

@LogHttpErrors
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class HttpErrorLoggingInterceptor {
  private static final Logger LOG = Logger.getLogger(HttpErrorLoggingInterceptor.class);

  @AroundInvoke
  Object logUnexpectedError(InvocationContext context) throws Exception {
    try {
      return context.proceed();
    } catch (Exception exception) {
      if (isExpectedHttpError(exception)) {
        LOG.warn("Falha HTTP esperada durante a execucao do recurso");
      } else {
        LOG.error("Erro inesperado durante a execucao do recurso HTTP", exception);
      }
      throw exception;
    }
  }

  private boolean isExpectedHttpError(Exception exception) {
    return exception instanceof AutenticacaoException
        || exception instanceof RegraNegocioException
        || exception instanceof RecursoNaoEncontradoException
        || exception instanceof ValidacaoException
        || exception instanceof WebApplicationException
        || exception.getClass().getSimpleName().contains("ViolationException");
  }
}
