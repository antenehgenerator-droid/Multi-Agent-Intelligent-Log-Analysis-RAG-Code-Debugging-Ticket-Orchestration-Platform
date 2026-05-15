package com.platform.api.controller;

import com.platform.api.kafka.DlqPeekService;
import com.platform.api.kafka.DlqPeekService.DlqMessageView;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnBean(DlqPeekService.class)
public class DlqAdminController {

  private final DlqPeekService dlqPeekService;

  public DlqAdminController(DlqPeekService dlqPeekService) {
    this.dlqPeekService = dlqPeekService;
  }

  /** Read-only inspection of DLQ records (newest-tail biased). */
  @GetMapping("/dlq")
  public ResponseEntity<List<DlqMessageView>> dlq(
      @RequestParam("topic") String topic, @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(dlqPeekService.peek(topic, limit));
  }
}
