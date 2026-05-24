package com.work.membership_service.configuration.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// logs one line for every incoming request and one for the response with status + duration
// runs just after MdcFilter so the trace id is already on the mdc
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RequestMethodLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        log.info("[http] incoming method: {}, path: {}",
                request.getMethod(), request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            long took = System.currentTimeMillis() - start;
            log.info("[http] completed method: {}, path: {}, status: {}, tookMs: {}",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), took);
        }
    }
}
