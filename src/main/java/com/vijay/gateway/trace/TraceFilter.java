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

@Component
public class TraceFilter extends OncePerRequestFilter {

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

	private static String resolveTraceId(HttpServletRequest request) {
		String incomingTraceId = request.getHeader(TraceConstants.X_TRACE_ID);
		if (StringUtils.hasText(incomingTraceId)) {
			return incomingTraceId;
		}
		return UUID.randomUUID().toString();
	}
}
