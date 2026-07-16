package com.finovago.p2p.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ResponseTimeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        ResponseWrapper wrappedResponse = new ResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            wrappedResponse.addHeader("X-Response-Time", String.valueOf(duration));
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private static class ResponseWrapper extends HttpServletResponseWrapper {
        public ResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void addHeader(String name, String value) {
            if (!isCommitted()) {
                super.addHeader(name, value);
            }
        }
    }
}
