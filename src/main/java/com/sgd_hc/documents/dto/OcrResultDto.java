package com.sgd_hc.documents.dto;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OcrResultDto(
        @JsonProperty("raw_text")       String rawText,
        @JsonProperty("structured_data")  Map<String, Object> structuredData,
        @JsonProperty("confidence_score") Double confidenceScore,
        @JsonProperty("pages_processed")  Integer pagesProcessed,
        @JsonProperty("file_type")        String fileType
) {}