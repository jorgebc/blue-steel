package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QueryResponseParser")
class QueryResponseParserTest {

  private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final QueryResponseParser parser = new QueryResponseParser(new ObjectMapper());

  @Test
  @DisplayName("should map valid JSON into a QueryResponse with grounded citations")
  void parse_validJson_mapsToQueryResponse() {
    String json =
        "{\"answer\":\"Aragorn is a ranger.\",\"citations\":["
            + "{\"session_id\":\""
            + SESSION_ID
            + "\",\"sequence_number\":3,\"snippet\":\"the ranger\"}]}";

    QueryResponse response = parser.parse(json);

    assertThat(response.answer()).isEqualTo("Aragorn is a ranger.");
    assertThat(response.citations()).containsExactly(new Citation(SESSION_ID, 3, "the ranger"));
  }

  @Test
  @DisplayName("should strip markdown code fences before parsing")
  void parse_codeFencedJson_isParsed() {
    String fenced =
        "```json\n{\"answer\":\"A\",\"citations\":[{\"session_id\":\""
            + SESSION_ID
            + "\",\"sequence_number\":1,\"snippet\":\"s\"}]}\n```";

    QueryResponse response = parser.parse(fenced);

    assertThat(response.answer()).isEqualTo("A");
    assertThat(response.citations()).containsExactly(new Citation(SESSION_ID, 1, "s"));
  }

  @Test
  @DisplayName("should map an empty citations array to an empty list")
  void parse_emptyCitations_returnsEmptyList() {
    QueryResponse response = parser.parse("{\"answer\":\"I don't know.\",\"citations\":[]}");

    assertThat(response.answer()).isEqualTo("I don't know.");
    assertThat(response.citations()).isEmpty();
  }

  @Test
  @DisplayName("should map a missing citations field to an empty list")
  void parse_missingCitations_returnsEmptyList() {
    QueryResponse response = parser.parse("{\"answer\":\"hi\"}");

    assertThat(response.citations()).isEmpty();
  }

  @Test
  @DisplayName("should throw rather than return empty citations when JSON is malformed")
  void parse_malformedJson_throws() {
    assertThatThrownBy(() -> parser.parse("this is not json"))
        .isInstanceOf(QueryResponseParseException.class);
  }
}
