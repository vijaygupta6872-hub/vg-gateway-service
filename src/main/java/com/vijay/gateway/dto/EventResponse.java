package com.vijay.gateway.dto;

import com.vijay.gateway.domain.EventStatus;
import com.vijay.gateway.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record EventResponse(
		String eventId,
		String accountId,
		EventType type,
		BigDecimal amount,
		String currency,
		Instant eventTimestamp,
		Map<String, Object> metadata,
		EventStatus status,
		Instant createdAt
) {
}
