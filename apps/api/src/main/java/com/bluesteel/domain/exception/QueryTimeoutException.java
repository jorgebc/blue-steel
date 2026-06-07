package com.bluesteel.domain.exception;

/** Thrown when a synchronous Query Mode request exceeds its configured deadline (D-052). */
public class QueryTimeoutException extends RuntimeException {

  public QueryTimeoutException() {
    super("The query exceeded its time budget");
  }
}
