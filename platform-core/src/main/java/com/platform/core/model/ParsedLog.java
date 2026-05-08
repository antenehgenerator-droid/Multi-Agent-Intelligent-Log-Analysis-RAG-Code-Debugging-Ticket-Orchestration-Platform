package com.platform.core.model;

import com.platform.core.model.LogIngestionRequest.LogEntry;
import java.util.Map;

public record ParsedLog(
    LogEntry raw, String template, String fingerprint, Map<String, Object> structured) {}

