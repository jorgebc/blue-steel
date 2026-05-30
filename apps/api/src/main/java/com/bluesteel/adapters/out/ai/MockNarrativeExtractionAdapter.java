package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.port.out.ingestion.NarrativeExtractionPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Returns a deterministic canned {@link ExtractionResult} (zero API cost). Active when no real LLM
 * provider profile is selected.
 */
@Component
@Profile("!llm-real & !llm-ollama")
public class MockNarrativeExtractionAdapter implements NarrativeExtractionPort {

  @Override
  public ExtractionResult extract(String rawSummaryText) {
    return new ExtractionResult(
        "The party encountered Mira and ventured into Thornwick.",
        List.of(
            new ExtractedMention("Mira", "A wandering healer known to the party", rawSummaryText),
            new ExtractedMention("Aldric", "A mysterious new stranger", rawSummaryText)),
        List.of(
            new ExtractedMention(
                "Thornwick", "A ruined village in the eastern marshes", rawSummaryText)),
        List.of(
            new ExtractedMention(
                "The Arrival", "The party arrives at Thornwick at dusk", rawSummaryText)),
        List.of(
            new ExtractedMention(
                "Mira guides the party", "Mira leads the group to shelter", rawSummaryText)));
  }
}
