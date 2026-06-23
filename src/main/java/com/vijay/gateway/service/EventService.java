package com.vijay.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vijay.gateway.client.AccountServiceClient;
import com.vijay.gateway.client.AccountServiceUnavailableException;
import com.vijay.gateway.client.AccountTransactionRequest;
import com.vijay.gateway.domain.EventRecord;
import com.vijay.gateway.domain.EventStatus;
import com.vijay.gateway.dto.CreateEventRequest;
import com.vijay.gateway.dto.EventResponse;
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

	@Transactional
	public EventServiceResult createEvent(CreateEventRequest request) {
		return eventRecordRepository.findByEventId(request.eventId())
				.map(existing -> {
					duplicateEventsCounter.increment();
					return new EventServiceResult(toResponse(existing), true);
				})
				.orElseGet(() -> createNewEvent(request));
	}

	@Transactional(readOnly = true)
	public EventResponse getEvent(String eventId) {
		return eventRecordRepository.findByEventId(eventId)
				.map(this::toResponse)
				.orElseThrow(() -> new EventNotFoundException(eventId));
	}

	@Transactional(readOnly = true)
	public List<EventResponse> listEvents(String accountId) {
		return eventRecordRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
				.stream()
				.map(this::toResponse)
				.toList();
	}

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
