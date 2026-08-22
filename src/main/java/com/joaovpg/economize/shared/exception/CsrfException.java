package com.joaovpg.economize.shared.exception;

public class CsrfException extends RuntimeException {
  public CsrfException() {
    super("Token CSRF ausente ou invalido");
  }
}
