package com.vijay.gateway.api;

import com.vijay.gateway.dto.CreateEventRequest;
import com.vijay.gateway.dto.EventResponse;
import com.vijay.gateway.dto.HealthResponse;
import com.vijay.gateway.service.EventService;
import com.vijay.gateway.service.EventServiceResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping("/events")
	public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
		EventServiceResult result = eventService.createEvent(request);
		HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
		return ResponseEntity.status(status).body(result.event());
	}

	@GetMapping("/events/{id}")
	public ResponseEntity<EventResponse> getEvent(@PathVariable String id) {
		return ResponseEntity.ok(eventService.getEvent(id));
	}

	@GetMapping(value = "/events", params = "account")
	public ResponseEntity<List<EventResponse>> listEvents(@RequestParam("account") @NotBlank String accountId) {
		return ResponseEntity.ok(eventService.listEvents(accountId));
	}

	@GetMapping("/health")
	public ResponseEntity<HealthResponse> health() {
		return ResponseEntity.ok(new HealthResponse("gateway-service", "UP"));
	}
}
