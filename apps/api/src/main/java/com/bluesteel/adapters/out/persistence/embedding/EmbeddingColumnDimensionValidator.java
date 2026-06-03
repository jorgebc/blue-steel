package com.bluesteel.adapters.out.persistence.embedding;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fails startup fast when the live {@code entity_embeddings.embedding} pgvector column dimension
 * does not match the configured embedding dimension ({@code
 * spring.liquibase.parameters.embeddingDimension}).
 *
 * <p>The column is created once by Liquibase changeset {@code 0016} and never altered, so switching
 * the embedding model/provider (Gemini 1536 vs Ollama 1024) without recreating the column leaves a
 * silent mismatch: inserts in {@code EntityEmbeddingWriteAdapter} fail, but {@code
 * EmbeddingGenerationListener} swallows the error, so embeddings are quietly never written (D-088,
 * D-093). This guard surfaces that loudly at boot instead.
 *
 * <p>No-ops when no {@link JdbcTemplate} is available (DataSource-less slice tests) or when the
 * column cannot be read; it only throws on a definitive dimension mismatch.
 */
@Component
public class EmbeddingColumnDimensionValidator implements ApplicationRunner {

  private static final Logger log =
      LoggerFactory.getLogger(EmbeddingColumnDimensionValidator.class);

  static final String COLUMN_TYPE_SQL =
      "SELECT format_type(a.atttypid, a.atttypmod)"
          + " FROM pg_attribute a"
          + " WHERE a.attrelid = 'entity_embeddings'::regclass"
          + " AND a.attname = 'embedding'"
          + " AND NOT a.attisdropped";

  /** Matches the dimension inside a pgvector type rendering such as {@code vector(1536)}. */
  private static final Pattern VECTOR_DIMENSION = Pattern.compile("vector\\((\\d+)\\)");

  private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
  private final int configuredDimension;

  public EmbeddingColumnDimensionValidator(
      ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
      @Value("${spring.liquibase.parameters.embeddingDimension}") int configuredDimension) {
    this.jdbcTemplateProvider = jdbcTemplateProvider;
    this.configuredDimension = configuredDimension;
  }

  @Override
  public void run(ApplicationArguments args) {
    JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    if (jdbcTemplate == null) {
      log.debug("No JdbcTemplate available — skipping embedding column dimension check");
      return;
    }

    String columnType;
    try {
      columnType = jdbcTemplate.queryForObject(COLUMN_TYPE_SQL, String.class);
    } catch (DataAccessException e) {
      log.warn(
          "Could not read entity_embeddings.embedding column type — skipping dimension check", e);
      return;
    }

    Integer actualDimension = parseDimension(columnType);
    if (actualDimension == null) {
      log.warn(
          "entity_embeddings.embedding has no fixed dimension (type={}) — skipping dimension check",
          columnType);
      return;
    }

    if (actualDimension != configuredDimension) {
      throw new IllegalStateException(
          ("Embedding dimension mismatch: entity_embeddings.embedding is vector(%d) but the configured"
                  + " embeddingDimension is %d. Embeddings from the active model cannot be stored. Recreate"
                  + " the column at the correct dimension (D-088, D-093): DROP TABLE entity_embeddings"
                  + " CASCADE; DELETE FROM databasechangelog WHERE id ="
                  + " '0016-create-entity-embeddings'; then redeploy so Liquibase re-applies it.")
              .formatted(actualDimension, configuredDimension));
    }

    log.info("Embedding column dimension check passed: vector({})", actualDimension);
  }

  private static Integer parseDimension(String columnType) {
    if (columnType == null) {
      return null;
    }
    Matcher matcher = VECTOR_DIMENSION.matcher(columnType);
    return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
  }
}
