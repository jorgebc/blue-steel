package com.bluesteel.adapters.out.persistence.embedding;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("EmbeddingColumnDimensionValidator")
class EmbeddingColumnDimensionValidatorTest {

  @SuppressWarnings("unchecked")
  private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider = mock(ObjectProvider.class);

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

  @Test
  @DisplayName("should pass when the column dimension matches the configured embedding dimension")
  void run_whenDimensionsMatch_doesNotThrow() {
    when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
    when(jdbcTemplate.queryForObject(
            EmbeddingColumnDimensionValidator.COLUMN_TYPE_SQL, String.class))
        .thenReturn("vector(1536)");

    EmbeddingColumnDimensionValidator validator =
        new EmbeddingColumnDimensionValidator(jdbcTemplateProvider, 1536);

    assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should throw when the column dimension differs from the configured dimension")
  void run_whenDimensionsDiffer_throws() {
    when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
    when(jdbcTemplate.queryForObject(
            EmbeddingColumnDimensionValidator.COLUMN_TYPE_SQL, String.class))
        .thenReturn("vector(1024)");

    EmbeddingColumnDimensionValidator validator =
        new EmbeddingColumnDimensionValidator(jdbcTemplateProvider, 1536);

    assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("vector(1024)")
        .hasMessageContaining("1536");
  }

  @Test
  @DisplayName("should no-op when no JdbcTemplate is available (DataSource-less context)")
  void run_whenNoJdbcTemplate_doesNotThrow() {
    when(jdbcTemplateProvider.getIfAvailable()).thenReturn(null);

    EmbeddingColumnDimensionValidator validator =
        new EmbeddingColumnDimensionValidator(jdbcTemplateProvider, 1536);

    assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("should skip the check when the column has no fixed vector dimension")
  void run_whenColumnHasNoDimension_doesNotThrow() {
    when(jdbcTemplateProvider.getIfAvailable()).thenReturn(jdbcTemplate);
    when(jdbcTemplate.queryForObject(
            EmbeddingColumnDimensionValidator.COLUMN_TYPE_SQL, String.class))
        .thenReturn("vector");

    EmbeddingColumnDimensionValidator validator =
        new EmbeddingColumnDimensionValidator(jdbcTemplateProvider, 1536);

    assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
        .doesNotThrowAnyException();
  }
}
