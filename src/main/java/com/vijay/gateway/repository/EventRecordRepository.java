package com.vijay.gateway.repository;

import com.vijay.gateway.domain.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRecordRepository extends JpaRepository<EventRecord, Long> {

	Optional<EventRecord> findByEventId(String eventId);

	List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
