package com.vijay.gateway.service;

public class EventNotFoundException extends RuntimeException {

	public EventNotFoundException(String eventId) {
		super("Event not found: " + eventId);
	}
}
