package com.bluesteel.application.port.out.ingestion;

import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import java.util.List;

/**
 * Driven port: detects contradictions between the extracted session narrative and the established
 * world state. Returns warning cards shown to the GM in the diff review (D-033); warnings are
 * non-blocking — the user must acknowledge them but may still commit.
 *
 * <p>{@code contentLanguage} is the campaign's immutable content-language code ({@code en}/{@code
 * es}, D-103); the real adapter instructs the LLM to write conflict descriptions in that language.
 */
public interface ConflictDetectionPort {

  List<ConflictWarning> detect(
      ExtractionResult extraction, List<EntityContext> relevantContext, String contentLanguage);
}
