package com.vijay.gateway.api;

import com.vijay.gateway.client.AccountServiceUnavailableException;
import com.vijay.gateway.dto.ErrorResponse;
import com.vijay.gateway.service.EventNotFoundException;
import com.vijay.gateway.trace.TraceConstants;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

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

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		String message = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return errorResponse(status, message);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		String message = ex.getConstraintViolations()
				.stream()
				.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
				.collect(Collectors.joining("; "));
		return errorResponse(status, message);
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
