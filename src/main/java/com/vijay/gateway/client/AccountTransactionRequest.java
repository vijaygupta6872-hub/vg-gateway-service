package com.vijay.gateway.client;

import com.vijay.gateway.domain.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record AccountTransactionRequest(
		String eventId,
		EventType type,
		BigDecimal amount,
		String currency,
		Instant eventTimestamp,
		Map<String, Object> metadata
) {
}
