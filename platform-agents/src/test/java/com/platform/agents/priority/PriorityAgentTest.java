package com.platform.agents.priority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.platform.agents.llm.PriorityLlmClient;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.DraftTicket;
import com.platform.core.model.PrioritizedTicket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class PriorityAgentTest {

  @Test
  void execute_paymentServiceOffHours_assignsP0() {
    Clock offHours = Clock.fixed(Instant.parse("2026-05-19T22:00:00Z"), ZoneOffset.UTC);
    PriorityAgent agent =
        new PriorityAgent(
            mock(PriorityLlmClient.class), new SimpleMeterRegistry(), offHours, new DefaultResourceLoader());

    AgentContext ctx = AgentContext.fresh();
    ctx.put("service", "payment-service");

    DraftTicket draft =
        new DraftTicket("title", "desc", "", List.of("Pet.java"), "fix");

    PrioritizedTicket result = agent.execute(draft, ctx);

    assertThat(result.calculatedSeverity()).isEqualTo("P0");
    assertThat(result.routingQueue()).isEqualTo("ENGINEERING_QUEUE");
  }
}
