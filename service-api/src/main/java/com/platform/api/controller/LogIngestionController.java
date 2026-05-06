package com.platform.api.controller;

import com.platform.api.service.IngestionService;
import com.platform.core.model.LogIngestionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController {

    private final IngestionService ingestionService;

    public LogIngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(":ingest")
    public ResponseEntity<Void> ingest(@Valid @RequestBody LogIngestionRequest request) {
        ingestionService.ingest(request.events());
        return ResponseEntity.accepted().build();
    }
}
