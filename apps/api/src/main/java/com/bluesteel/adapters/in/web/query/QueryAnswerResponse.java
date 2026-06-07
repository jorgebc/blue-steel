package com.bluesteel.adapters.in.web.query;

import java.util.List;

/** Response body for {@code POST /api/v1/campaigns/{id}/queries}: the answer plus its citations. */
public record QueryAnswerResponse(String answer, List<CitationResponse> citations) {}
