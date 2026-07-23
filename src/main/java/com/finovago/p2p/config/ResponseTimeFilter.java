package com.finovago.p2p.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

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
            // so the header can be set directly - no need to wrap.
            long duration = System.currentTimeMillis() - (long) request.getAttribute(START_TIME_ATTRIBUTE);
            response.setHeader("X-Response-Time", String.valueOf(duration));
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);

        // Setting the header in a finally block AFTER filterChain.doFilter() returns is too late for
        // most real responses: the container commits the response (headers included) as soon as the
        // body starts being written, which happens INSIDE that call - not after it. Once committed,
        // setHeader() is a silent no-op per the Servlet spec. This wrapper sets the header lazily, the
        // moment the body's output stream/writer is first requested - i.e. right before the first byte
        // could possibly be written, which is always before commit. It never buffers or intercepts the
        // actual bytes, so - unlike a fully buffering wrapper - it carries none of the async-lifecycle
        // risk called out in TimingResponseWrapper's Javadoc below.
        TimingResponseWrapper wrapped = new TimingResponseWrapper(response, startTime);

        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            // Fallback for responses with no body at all (e.g. 204 No Content): the wrapper's streams
            // are never requested in that case, so the header would otherwise never get set. Safe to
            // set here too since nothing has been written.
            if (!request.isAsyncStarted() && !wrapped.headerWritten()) {
                response.setHeader("X-Response-Time", String.valueOf(System.currentTimeMillis() - startTime));
            }
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        // Default is true (skip on the internal /error forward Boot performs for unmapped routes
        // and uncaught exceptions), which would leave error-page responses untimed. Re-run so those
        // get X-Response-Time too, consistent with "every response has this header".
        return false;
    }

    /**
     * Adds X-Response-Time the moment the response body starts being written, instead of after the
     * fact. Purely pass-through - it never buffers bytes itself, so (unlike e.g.
     * ContentCachingResponseWrapper) there is no buffered content that could be silently dropped if
     * this dispatch turns out to start async processing further down the chain.
     */
    private static final class TimingResponseWrapper extends HttpServletResponseWrapper {
        private final long startTime;
        private boolean headerWritten = false;

        TimingResponseWrapper(HttpServletResponse response, long startTime) {
            super(response);
            this.startTime = startTime;
        }

        boolean headerWritten() {
            return headerWritten;
        }

        private void writeHeaderOnce() {
            if (!headerWritten) {
                headerWritten = true;
                if (getHeader("X-Response-Time") == null) {
                    setHeader("X-Response-Time", String.valueOf(System.currentTimeMillis() - startTime));
                }
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            ServletOutputStream delegate = super.getOutputStream();
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    delegate.setWriteListener(writeListener);
                }

                @Override
                public void write(int b) throws IOException {
                    writeHeaderOnce();
                    delegate.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    writeHeaderOnce();
                    delegate.write(b, off, len);
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            writeHeaderOnce();
            return super.getWriter();
        }
    }
}
