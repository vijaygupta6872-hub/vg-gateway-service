package com.vijay.gateway.api;

import com.vijay.gateway.client.AccountServiceUnavailableException;
import com.vijay.gateway.dto.ErrorResponse;
import com.vijay.gateway.service.EventNotFoundException;
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
		return errorResponse(status, ex.getMessage());
	}

	@ExceptionHandler(EventNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleEventNotFound(EventNotFoundException ex) {
		HttpStatus status = HttpStatus.NOT_FOUND;
		return errorResponse(status, ex.getMessage());
	}

	private static ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String message) {
		ErrorResponse response = new ErrorResponse(
				MDC.get(TraceConstants.MDC_TRACE_ID),
				status.value(),
				status.getReasonPhrase(),
				message,
				Instant.now()
		);
		return ResponseEntity.status(status).body(response);
	}
}
