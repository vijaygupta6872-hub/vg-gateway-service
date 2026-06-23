package com.vijay.gateway.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes Gateway request tracing.
 * <p>
 * The filter reads an incoming {@code X-Trace-Id} header when supplied, otherwise
 * generates a new UUID trace identifier. The trace ID is stored in MDC under the
 * {@code traceId} key so application logs can include it, and it is written back
 * to the HTTP response for client-side correlation. MDC state is cleared after
 * request processing to prevent trace leakage between reused servlet threads.
 * </p>
 */
@Component
public class TraceFilter extends OncePerRequestFilter {

	/**
	 * Resolves, stores, propagates, and clears the request trace identifier.
	 *
	 * @param request current HTTP servlet request
	 * @param response current HTTP servlet response
	 * @param filterChain remaining servlet filter chain to invoke
	 * @throws ServletException if downstream filter or servlet processing fails
	 * @throws IOException if downstream filter or servlet processing encounters an
	 *         I/O error
	 */
	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String traceId = resolveTraceId(request);
		MDC.put(TraceConstants.MDC_TRACE_ID, traceId);
		response.setHeader(TraceConstants.X_TRACE_ID, traceId);

		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(TraceConstants.MDC_TRACE_ID);
		}
	}

	/**
	 * Resolves the trace identifier for the current request.
	 *
	 * @param request HTTP request that may contain an {@code X-Trace-Id} header
	 * @return incoming trace identifier when present and nonblank; otherwise a new
	 *         UUID string
	 */
	private static String resolveTraceId(HttpServletRequest request) {
		String incomingTraceId = request.getHeader(TraceConstants.X_TRACE_ID);
		if (StringUtils.hasText(incomingTraceId)) {
			return incomingTraceId;
		}
		return UUID.randomUUID().toString();
	}
}
