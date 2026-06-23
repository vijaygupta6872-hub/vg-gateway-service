package com.vijay.gateway.api;

import com.vijay.gateway.client.AccountServiceUnavailableException;
import com.vijay.gateway.dto.ErrorResponse;
import com.vijay.gateway.trace.TraceConstants;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AccountServiceUnavailableException.class)
	public ResponseEntity<ErrorResponse> handleAccountServiceUnavailable(AccountServiceUnavailableException ex) {
		HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
		ErrorResponse response = new ErrorResponse(
				MDC.get(TraceConstants.MDC_TRACE_ID),
				status.value(),
				status.getReasonPhrase(),
				ex.getMessage(),
				Instant.now()
		);
		return ResponseEntity.status(status).body(response);
	}
}
