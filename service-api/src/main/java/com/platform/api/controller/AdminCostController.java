package com.platform.api.controller;

import com.platform.llm.budget.BudgetEnforcer;
import com.platform.llm.model.BudgetState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCostController {

  private final BudgetEnforcer budgetEnforcer;

  public AdminCostController(BudgetEnforcer budgetEnforcer) {
    this.budgetEnforcer = budgetEnforcer;
  }

  @GetMapping("/cost/today")
  public ResponseEntity<Map<String, Object>> getCostDashboardMetadata() {
    BudgetState state = budgetEnforcer.getCurrentState();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("current_spend_usd", state.currentSpend());
    body.put("daily_budget_limit_usd", state.maxLimit());
    body.put("utilization_percentage", state.utilizationRatio() * 100);
    body.put("active_degradation_tier", state.getLevel().name());
    return ResponseEntity.ok(body);
  }
}
