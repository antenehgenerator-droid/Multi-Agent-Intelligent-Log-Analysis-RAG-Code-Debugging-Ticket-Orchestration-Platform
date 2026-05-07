package com.platform.api.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MdcFilter implements Filter {
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      // In prod, extract X-Trace-Id header if it exists, otherwise generate
      String traceId = ((HttpServletRequest) request).getHeader("X-Trace-Id");
      MDC.put("traceId", traceId != null ? traceId : UUID.randomUUID().toString());
      MDC.put("spanId", UUID.randomUUID().toString().substring(0, 8));

      chain.doFilter(request, response);
    } finally {
      // ALWAYS clear to prevent memory leaks in virtual threads/thread pools
      MDC.clear();
    }
  }
}
