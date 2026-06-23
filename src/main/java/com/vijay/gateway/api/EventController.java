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

/**
 * REST controller that exposes the Gateway Service event API.
 * <p>
 * This controller is responsible for translating HTTP requests into Gateway
 * application operations. It delegates business processing to {@link EventService}
 * and selects the correct HTTP status for new versus duplicate event submissions.
 * The controller also exposes a lightweight Gateway health endpoint used by
 * local checks and simple service monitoring.
 * </p>
 */
@Validated
@RestController
public class EventController {

	private final EventService eventService;

	/**
	 * Creates a controller backed by the event application service.
	 *
	 * @param eventService service that owns event idempotency, persistence, and
	 *                     Account Service coordination
	 */
	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	/**
	 * Creates an event or returns the already persisted event for a duplicate
	 * {@code eventId}.
	 * <p>
	 * New events are forwarded to Account Service before being persisted. Duplicate
	 * submissions are treated as idempotent retries and do not call Account Service
	 * again.
	 * </p>
	 *
	 * @param request validated event creation request supplied in the HTTP request
	 *                body
	 * @return {@link ResponseEntity} containing the event response with
	 *         {@code 201 Created} for a new event or {@code 200 OK} for a duplicate
	 * @throws com.vijay.gateway.exception.AccountServiceUnavailableException if the
	 *         downstream Account Service is unavailable while processing a new event
	 */
	@PostMapping("/events")
	public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
		EventServiceResult result = eventService.createEvent(request);
		HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
		return ResponseEntity.status(status).body(result.event());
	}

	/**
	 * Retrieves a single event by its business event identifier.
	 *
	 * @param id event identifier from the request path
	 * @return {@link ResponseEntity} containing the matching event
	 * @throws com.vijay.gateway.exception.EventNotFoundException if no event exists
	 *         for the supplied identifier
	 */
	@GetMapping("/events/{id}")
	public ResponseEntity<EventResponse> getEvent(@PathVariable String id) {
		return ResponseEntity.ok(eventService.getEvent(id));
	}

	/**
	 * Lists all events for an account ordered by event timestamp ascending.
	 *
	 * @param accountId account identifier supplied as the {@code account} query
	 *                  parameter
	 * @return {@link ResponseEntity} containing zero or more account events ordered
	 *         from oldest to newest
	 */
	@GetMapping(value = "/events", params = "account")
	public ResponseEntity<List<EventResponse>> listEvents(@RequestParam("account") @NotBlank String accountId) {
		return ResponseEntity.ok(eventService.listEvents(accountId));
	}

	/**
	 * Returns a simple Gateway health response.
	 *
	 * @return {@link ResponseEntity} containing service name and status
	 */
	@GetMapping("/health")
	public ResponseEntity<HealthResponse> health() {
		return ResponseEntity.ok(new HealthResponse("gateway-service", "UP"));
	}
}
