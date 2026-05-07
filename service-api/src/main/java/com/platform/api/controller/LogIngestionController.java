package com.platform.api.controller;

import com.platform.api.service.IngestionService;
import com.platform.core.model.LogIngestionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class LogIngestionController {

  private final IngestionService ingestionService;

  public LogIngestionController(IngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @PostMapping("/api/v1/logs:ingest")
  public ResponseEntity<Void> ingest(@Valid @RequestBody LogIngestionRequest request) {
    ingestionService.ingest(request.events());
    return ResponseEntity.accepted().build();
  }
}
