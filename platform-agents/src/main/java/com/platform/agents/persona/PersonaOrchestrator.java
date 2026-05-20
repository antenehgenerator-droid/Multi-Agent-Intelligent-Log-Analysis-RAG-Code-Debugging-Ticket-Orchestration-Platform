package com.platform.agents.persona;

import com.platform.agents.model.PerspectiveFinding;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PersonaOrchestrator {

  private static final Pattern STACK_TRACE_PATTERN =
      Pattern.compile("at\\s+([a-zA-Z0-9._$]+)\\.([a-zA-Z0-9_$]+)\\(");

  private final List<BasePersonaAgent> personas;

  public PersonaOrchestrator(List<BasePersonaAgent> personas) {
    this.personas = personas;
  }

  public List<PerspectiveFinding> coordinateSwarm(
      String requirement, String stackTrace, String rawDbCodeChunks) {
    String anchoredCodeContext = anchorContextFilter(stackTrace, rawDbCodeChunks);

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<PerspectiveFinding>> futures = new ArrayList<>();
      for (BasePersonaAgent persona : personas) {
        futures.add(
            executor.submit(
                () -> persona.generatePerspective(requirement, stackTrace, anchoredCodeContext)));
      }

      List<PerspectiveFinding> results = new ArrayList<>(futures.size());
      for (Future<PerspectiveFinding> future : futures) {
        try {
          results.add(future.get());
        } catch (ExecutionException e) {
          throw new RuntimeException("Subtask transaction pool failed", e.getCause());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Parallel execution pipeline was aborted", e);
        }
      }
      return results;
    }
  }

  private String anchorContextFilter(String stackTrace, String codeContext) {
    Matcher matcher = STACK_TRACE_PATTERN.matcher(stackTrace);
    if (matcher.find()) {
      String extractedFqn = matcher.group(1);
      return String.format(
          "--- CRITICAL STACK CONTEXT MATCHED FOR CLASS %s ---\n%s", extractedFqn, codeContext);
    }
    return codeContext;
  }
}
