package com.finovago.p2p.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        try {
            String correlationId = UUID.randomUUID().toString(); // generate a correlationId for each HTTP request

            MDC.put("correlationId", correlationId); // inject it into the MDC context map (for the HTTP thread)

            response.addHeader("X-Correlation-Id", correlationId); // send it in the HTTP response header

            filterChain.doFilter(request, response); 

        } finally {
            MDC.clear(); // clear the MDC context map when the HTTP request is finished
        }
    }
}