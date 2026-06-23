package com.vijay.gateway.service;

import com.vijay.gateway.dto.EventResponse;

public record EventServiceResult(
		EventResponse event,
		boolean duplicate
) {
}
