package com.vijay.gateway.dto;

import com.vijay.gateway.domain.EventType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record CreateEventRequest(
		@NotBlank String eventId,
		@NotBlank String accountId,
		@NotNull EventType type,
		@NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
		@NotBlank String currency,
		@NotNull Instant eventTimestamp,
		Map<String, Object> metadata
) {
}
