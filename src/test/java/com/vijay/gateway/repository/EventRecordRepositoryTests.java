package com.vijay.gateway.repository;

import com.vijay.gateway.domain.EventRecord;
import com.vijay.gateway.domain.EventStatus;
import com.vijay.gateway.domain.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EventRecordRepositoryTests {

	@Autowired
	private EventRecordRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void canSaveAndFindByEventId() {
		EventRecord saved = repository.save(eventRecord("event-1", "account-1", Instant.parse("2026-06-22T10:00:00Z")));

		Optional<EventRecord> found = repository.findByEventId("event-1");

		assertThat(found).isPresent();
		assertThat(found.get().getId()).isEqualTo(saved.getId());
		assertThat(found.get().getAccountId()).isEqualTo("account-1");
	}

	@Test
	void duplicateEventIdViolatesUniqueness() {
		repository.saveAndFlush(eventRecord("event-1", "account-1", Instant.parse("2026-06-22T10:00:00Z")));
		entityManager.clear();

		EventRecord duplicate = eventRecord("event-1", "account-2", Instant.parse("2026-06-22T11:00:00Z"));

		assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void accountEventsAreReturnedOrderedByEventTimestampAscending() {
		repository.save(eventRecord("event-late", "account-1", Instant.parse("2026-06-22T12:00:00Z")));
		repository.save(eventRecord("event-other", "account-2", Instant.parse("2026-06-22T09:00:00Z")));
		repository.save(eventRecord("event-early", "account-1", Instant.parse("2026-06-22T08:00:00Z")));
		repository.save(eventRecord("event-middle", "account-1", Instant.parse("2026-06-22T10:00:00Z")));
		repository.flush();

		List<EventRecord> records = repository.findByAccountIdOrderByEventTimestampAsc("account-1");

		assertThat(records)
				.extracting(EventRecord::getEventId)
				.containsExactly("event-early", "event-middle", "event-late");
	}

	private static EventRecord eventRecord(String eventId, String accountId, Instant eventTimestamp) {
		EventRecord record = new EventRecord();
		record.setEventId(eventId);
		record.setAccountId(accountId);
		record.setType(EventType.CREDIT);
		record.setAmount(new BigDecimal("10.00"));
		record.setCurrency("USD");
		record.setEventTimestamp(eventTimestamp);
		record.setStatus(EventStatus.APPLIED);
		record.setCreatedAt(Instant.parse("2026-06-22T00:00:00Z"));
		return record;
	}
}
