package com.work.membership_service.configuration.filter;

import com.work.membership_service.constant.StringConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// puts a trace id on the mdc for every request so logs can be tied together
// uses the caller-supplied X-Trace-Id header if present, otherwise generates one
// echoes it back on the response so the caller can correlate too
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String traceId = request.getHeader(StringConstant.HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(StringConstant.MDC_TRACE_ID, traceId);
        response.setHeader(StringConstant.HEADER_TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // always clear; pooled threads reuse this slot for the next request
            MDC.clear();
        }
    }
}
