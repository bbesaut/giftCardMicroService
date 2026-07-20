package com.finovago.p2p.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Must run before Spring Security's filter chain (default order -100), otherwise requests
// rejected by Security itself (401/403, before reaching the controller) never get timed.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResponseTimeFilter extends OncePerRequestFilter {

    private static final String START_TIME_ATTRIBUTE = "responseTimeFilter.startTime";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            // Resuming an async controller (e.g. redeem): nothing has been written yet at this point,
            // so the header can be set directly - no need to wrap. Wrapping here (e.g. with
            // ContentCachingResponseWrapper) would be unsafe: Spring's WebAsyncManager captures
            // whatever response object was active when the initial dispatch called startAsync() and
            // reuses it for the entire async lifecycle, so a wrapper created on the initial dispatch
            // never gets a chance to flush its buffered body, silently dropping it.
            long duration = System.currentTimeMillis() - (long) request.getAttribute(START_TIME_ATTRIBUTE);
            response.setHeader("X-Response-Time", String.valueOf(duration));
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!request.isAsyncStarted()) {
                long duration = System.currentTimeMillis() - startTime;
                response.setHeader("X-Response-Time", String.valueOf(duration));
            }
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
