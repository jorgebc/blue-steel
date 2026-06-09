package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses the LLM query-answering JSON ({@code {answer, citations:[{session_id, sequence_number,
 * snippet}]}}) into a {@link QueryResponse}. On parse failure it logs at ERROR and throws {@link
 * QueryResponseParseException} — it never silently returns an empty answer or citation list
 * (D-003).
 */
@Component
public class QueryResponseParser {

  private static final Logger log = LoggerFactory.getLogger(QueryResponseParser.class);

  /** LLM citation shape (snake_case as instructed in the system prompt). */
  record RawCitation(
      @JsonProperty("session_id") UUID sessionId,
      @JsonProperty("sequence_number") int sequenceNumber,
      @JsonProperty("snippet") String snippet) {}

  /** LLM answer shape. */
  record RawAnswer(
      @JsonProperty("answer") String answer,
      @JsonProperty("citations") List<RawCitation> citations) {}

  private final ObjectMapper objectMapper;

  public QueryResponseParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public QueryResponse parse(String rawContent) {
    String json = stripCodeFences(rawContent);
    RawAnswer raw;
    try {
      raw = objectMapper.readValue(json, RawAnswer.class);
    } catch (JsonProcessingException e) {
      // Do not log rawContent itself — it may contain campaign narrative (LOG-01).
      log.error("Failed to parse LLM query-answering response as JSON", e);
      throw new QueryResponseParseException("Unparseable LLM query response", e);
    }

    List<Citation> citations =
        raw.citations() == null
            ? List.of()
            : raw.citations().stream()
                .map(c -> new Citation(c.sessionId(), c.sequenceNumber(), c.snippet()))
                .toList();
    return new QueryResponse(raw.answer(), citations);
  }

  /** Strips a leading {@code ```}/{@code ```json} fence and trailing {@code ```}, if present. */
  private static String stripCodeFences(String raw) {
    String trimmed = raw == null ? "" : raw.strip();
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      if (firstNewline >= 0) {
        trimmed = trimmed.substring(firstNewline + 1);
      }
      if (trimmed.endsWith("```")) {
        trimmed = trimmed.substring(0, trimmed.length() - 3);
      }
    }
    return trimmed.strip();
  }
}
