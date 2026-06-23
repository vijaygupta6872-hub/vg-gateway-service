package com.vijay.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vijay.gateway.client.AccountServiceClient;
import com.vijay.gateway.client.AccountTransactionRequest;
import com.vijay.gateway.domain.EventRecord;
import com.vijay.gateway.domain.EventStatus;
import com.vijay.gateway.dto.CreateEventRequest;
import com.vijay.gateway.dto.EventResponse;
import com.vijay.gateway.exception.AccountServiceUnavailableException;
import com.vijay.gateway.exception.EventNotFoundException;
import com.vijay.gateway.repository.EventRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Application service that implements Gateway event business processing.
 * <p>
 * This service owns idempotent event creation, Account Service coordination,
 * persistence, response mapping, and Gateway event metrics. A new event is only
 * stored after Account Service successfully applies the corresponding account
 * transaction. If the event already exists, the persisted record is returned as
 * a duplicate without calling Account Service again.
 * </p>
 */
@Service
public class EventService {

	private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
	};

	private final EventRecordRepository eventRecordRepository;
	private final AccountServiceClient accountServiceClient;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final Counter acceptedEventsCounter;
	private final Counter duplicateEventsCounter;
	private final Counter accountServiceFailuresCounter;

	/**
	 * Creates the event service with persistence, downstream client, JSON mapper,
	 * and metric registry dependencies.
	 *
	 * @param eventRecordRepository repository used to read and write event records
	 * @param accountServiceClient client used to apply account transactions for new
	 *                             events
	 * @param objectMapper JSON mapper used to convert metadata between map and
	 *                     persisted JSON string representations
	 * @param meterRegistry registry used to create Gateway event counters
	 */
	public EventService(
			EventRecordRepository eventRecordRepository,
			AccountServiceClient accountServiceClient,
			ObjectMapper objectMapper,
			MeterRegistry meterRegistry
	) {
		this.eventRecordRepository = eventRecordRepository;
		this.accountServiceClient = accountServiceClient;
		this.objectMapper = objectMapper;
		this.clock = Clock.systemUTC();
		this.acceptedEventsCounter = meterRegistry.counter("gateway.events.accepted");
		this.duplicateEventsCounter = meterRegistry.counter("gateway.events.duplicates");
		this.accountServiceFailuresCounter = meterRegistry.counter("gateway.events.account_service_failures");
	}

	/**
	 * Creates a new event or returns the existing event for an idempotent retry.
	 * <p>
	 * Business rules:
	 * </p>
	 * <ul>
	 *   <li>If {@code eventId} already exists, return the stored event with
	 *   {@code duplicate=true}.</li>
	 *   <li>If {@code eventId} is new, call Account Service first.</li>
	 *   <li>Persist the event only after Account Service succeeds.</li>
	 *   <li>Do not persist the event when Account Service is unavailable.</li>
	 * </ul>
	 *
	 * @param request validated Gateway event creation request
	 * @return service result containing the event response and duplicate flag
	 * @throws AccountServiceUnavailableException if Account Service cannot apply
	 *         the transaction for a new event
	 * @throws IllegalArgumentException if metadata cannot be serialized as JSON
	 */
	@Transactional
	public EventServiceResult createEvent(CreateEventRequest request) {
		return eventRecordRepository.findByEventId(request.eventId())
				.map(existing -> {
					duplicateEventsCounter.increment();
					return new EventServiceResult(toResponse(existing), true);
				})
				.orElseGet(() -> createNewEvent(request));
	}

	/**
	 * Retrieves one event by business event identifier.
	 *
	 * @param eventId event identifier to look up
	 * @return event response for the matching persisted event
	 * @throws EventNotFoundException if no event exists for {@code eventId}
	 * @throws IllegalStateException if stored event metadata cannot be parsed
	 */
	@Transactional(readOnly = true)
	public EventResponse getEvent(String eventId) {
		return eventRecordRepository.findByEventId(eventId)
				.map(this::toResponse)
				.orElseThrow(() -> new EventNotFoundException(eventId));
	}

	/**
	 * Lists events for an account ordered by event timestamp ascending.
	 *
	 * @param accountId account identifier to filter by
	 * @return list of event responses ordered from oldest to newest; empty when no
	 *         events exist for the account
	 * @throws IllegalStateException if any stored event metadata cannot be parsed
	 */
	@Transactional(readOnly = true)
	public List<EventResponse> listEvents(String accountId) {
		return eventRecordRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
				.stream()
				.map(this::toResponse)
				.toList();
	}

	/**
	 * Creates and persists a new event after Account Service accepts the matching
	 * transaction.
	 *
	 * @param request validated event creation request
	 * @return service result containing the newly persisted event and
	 *         {@code duplicate=false}
	 * @throws AccountServiceUnavailableException if Account Service is unavailable
	 * @throws IllegalArgumentException if metadata cannot be serialized as JSON
	 */
	private EventServiceResult createNewEvent(CreateEventRequest request) {
		AccountTransactionRequest transactionRequest = new AccountTransactionRequest(
				request.eventId(),
				request.type(),
				request.amount(),
				request.currency(),
				request.eventTimestamp(),
				request.metadata()
		);

		try {
			accountServiceClient.postTransaction(request.accountId(), transactionRequest);
		} catch (AccountServiceUnavailableException ex) {
			accountServiceFailuresCounter.increment();
			throw ex;
		}

		EventRecord record = new EventRecord();
		record.setEventId(request.eventId());
		record.setAccountId(request.accountId());
		record.setType(request.type());
		record.setAmount(request.amount());
		record.setCurrency(request.currency());
		record.setEventTimestamp(request.eventTimestamp());
		record.setMetadataJson(toMetadataJson(request.metadata()));
		record.setStatus(EventStatus.APPLIED);
		record.setCreatedAt(Instant.now(clock));

		EventRecord saved = eventRecordRepository.save(record);
		acceptedEventsCounter.increment();
		return new EventServiceResult(toResponse(saved), false);
	}

	/**
	 * Converts a persistence entity into the external API response DTO.
	 *
	 * @param record persisted event entity to convert
	 * @return API response representing the persisted event
	 * @throws IllegalStateException if stored metadata JSON cannot be parsed
	 */
	private EventResponse toResponse(EventRecord record) {
		return new EventResponse(
				record.getEventId(),
				record.getAccountId(),
				record.getType(),
				record.getAmount(),
				record.getCurrency(),
				record.getEventTimestamp(),
				toMetadata(record.getMetadataJson()),
				record.getStatus(),
				record.getCreatedAt()
		);
	}

	/**
	 * Serializes optional request metadata for persistence.
	 *
	 * @param metadata optional metadata map from the API request
	 * @return JSON string for persistence, or {@code null} when metadata is absent
	 * @throws IllegalArgumentException if the metadata map cannot be serialized
	 */
	private String toMetadataJson(Map<String, Object> metadata) {
		if (metadata == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(metadata);
		} catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Metadata must be JSON serializable", ex);
		}
	}

	/**
	 * Deserializes persisted metadata JSON into an API response map.
	 *
	 * @param metadataJson persisted metadata JSON; may be {@code null} or blank
	 * @return metadata map for API responses, or {@code null} when no metadata is
	 *         stored
	 * @throws IllegalStateException if persisted metadata is not valid JSON
	 */
	private Map<String, Object> toMetadata(String metadataJson) {
		if (!StringUtils.hasText(metadataJson)) {
			return null;
		}
		try {
			return objectMapper.readValue(metadataJson, METADATA_TYPE);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Stored event metadata is not valid JSON", ex);
		}
	}
}
