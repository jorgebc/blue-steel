package com.bluesteel.adapters.out.ai;

/**
 * Thrown when the LLM query-answering response cannot be parsed into a {@code QueryResponse}. A
 * parse failure is never swallowed into an empty answer/citation list — it surfaces as an error so
 * an ungrounded response is never returned to the caller (D-003).
 */
public class QueryResponseParseException extends RuntimeException {

  public QueryResponseParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
