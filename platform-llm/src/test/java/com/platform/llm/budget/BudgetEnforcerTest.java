package com.platform.llm.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.llm.model.BudgetState;
import org.junit.jupiter.api.Test;

class BudgetEnforcerTest {

  @Test
  void verifyLadderTransitions_onExactBoundaries() {
    assertThat(new BudgetState(34.99, 50.0, 34.99 / 50.0).getLevel())
        .isEqualTo(BudgetState.DegradationLevel.NORMAL);
    assertThat(new BudgetState(35.00, 50.0, 35.00 / 50.0).getLevel())
        .isEqualTo(BudgetState.DegradationLevel.DEGRADED);
    assertThat(new BudgetState(44.99, 50.0, 44.99 / 50.0).getLevel())
        .isEqualTo(BudgetState.DegradationLevel.DEGRADED);
    assertThat(new BudgetState(45.00, 50.0, 45.00 / 50.0).getLevel())
        .isEqualTo(BudgetState.DegradationLevel.CRITICAL);
    assertThat(new BudgetState(50.00, 50.0, 50.00 / 50.0).getLevel())
        .isEqualTo(BudgetState.DegradationLevel.EXHAUSTED);
  }
}
