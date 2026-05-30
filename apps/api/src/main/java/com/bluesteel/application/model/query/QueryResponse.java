package com.bluesteel.application.model.query;

import java.util.List;

/**
 * The structured result of a natural-language world-state query: the answer text and its grounding
 * citations.
 */
public record QueryResponse(String answer, List<Citation> citations) {}
