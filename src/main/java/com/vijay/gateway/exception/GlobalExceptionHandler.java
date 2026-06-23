package com.vijay.gateway.exception;

import com.vijay.gateway.dto.ErrorResponse;
import com.vijay.gateway.trace.TraceConstants;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
		if (message.isBlank()) {
			message = "Request validation failed";
		}
		return errorResponse(status, message);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		return errorResponse(status, "Request body is missing or malformed");
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		String message = ex.getConstraintViolations()
				.stream()
				.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
				.collect(Collectors.joining("; "));
		if (message.isBlank()) {
			message = "Request validation failed";
		}
		return errorResponse(status, message);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		return errorResponse(status, "Unexpected server error");
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
