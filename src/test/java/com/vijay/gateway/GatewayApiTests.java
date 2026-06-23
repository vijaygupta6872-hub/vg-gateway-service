package com.vijay.gateway;

import com.vijay.gateway.client.AccountServiceClient;
import com.vijay.gateway.exception.AccountServiceUnavailableException;
import com.vijay.gateway.repository.EventRecordRepository;
import com.vijay.gateway.trace.TraceConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EventRecordRepository eventRecordRepository;

	@MockitoBean
	private AccountServiceClient accountServiceClient;

	@BeforeEach
	void setUp() {
		eventRecordRepository.deleteAll();
		reset(accountServiceClient);
	}

	@Test
	void postEventsWithValidEventReturnsCreated() throws Exception {
		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-1", "account-1", "CREDIT", "10.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventId").value("event-1"))
				.andExpect(jsonPath("$.accountId").value("account-1"))
				.andExpect(jsonPath("$.status").value("APPLIED"));

		assertThat(eventRecordRepository.findByEventId("event-1")).isPresent();
		verify(accountServiceClient).postTransaction(anyString(), any());
	}

	@Test
	void duplicatePostEventsReturnsOkAndDoesNotCallAccountServiceTwice() throws Exception {
		String event = eventJson("event-1", "account-1", "CREDIT", "10.00", "2026-06-22T10:00:00Z");

		mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(event))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(event))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value("event-1"));

		verify(accountServiceClient, times(1)).postTransaction(anyString(), any());
		assertThat(eventRecordRepository.findAll()).hasSize(1);
	}

	@Test
	void invalidEventMissingRequiredFieldsReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void invalidAmountLessThanOrEqualToZeroReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-1", "account-1", "DEBIT", "0.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")));
	}

	@Test
	void unknownTypeReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-1", "account-1", "TRANSFER", "10.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void getEventsByIdReturnsSavedEvent() throws Exception {
		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-1", "account-1", "CREDIT", "10.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/events/{id}", "event-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value("event-1"))
				.andExpect(jsonPath("$.accountId").value("account-1"));
	}

	@Test
	void getEventsByIdUnknownReturnsNotFound() throws Exception {
		mockMvc.perform(get("/events/{id}", "missing-event"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.message").value("Event not found: missing-event"));
	}

	@Test
	void getEventsByAccountReturnsEventsOrderedByEventTimestamp() throws Exception {
		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-late", "account-1", "CREDIT", "10.00", "2026-06-22T12:00:00Z")))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-other", "account-2", "CREDIT", "10.00", "2026-06-22T09:00:00Z")))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-early", "account-1", "CREDIT", "10.00", "2026-06-22T08:00:00Z")))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/events").param("account", "account-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].eventId").value("event-early"))
				.andExpect(jsonPath("$[1].eventId").value("event-late"));
	}

	@Test
	void accountServiceUnavailableReturnsServiceUnavailableAndDoesNotSaveEvent() throws Exception {
		doThrow(new AccountServiceUnavailableException("Account Service is unavailable", new RuntimeException("down")))
				.when(accountServiceClient).postTransaction(anyString(), any());

		mockMvc.perform(post("/events")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-1", "account-1", "CREDIT", "10.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.message").value("Account Service is unavailable"));

		assertThat(eventRecordRepository.findByEventId("event-1")).isEmpty();
	}

	@Test
	void xTraceIdIsGeneratedWhenMissing() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(header().exists(TraceConstants.X_TRACE_ID));
	}

	@Test
	void xTraceIdIsPropagatedToAccountServiceWhenPresent() throws Exception {
		AtomicReference<String> traceIdSeenByClient = new AtomicReference<>();
		doAnswer(invocation -> {
			traceIdSeenByClient.set(MDC.get(TraceConstants.MDC_TRACE_ID));
			return null;
		}).when(accountServiceClient).postTransaction(anyString(), any());

		mockMvc.perform(post("/events")
						.header(TraceConstants.X_TRACE_ID, "trace-123")
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("event-1", "account-1", "CREDIT", "10.00", "2026-06-22T10:00:00Z")))
				.andExpect(status().isCreated())
				.andExpect(header().string(TraceConstants.X_TRACE_ID, "trace-123"));

		assertThat(traceIdSeenByClient).hasValue("trace-123");
	}

	private static String eventJson(String eventId, String accountId, String type, String amount, String eventTimestamp) {
		return """
				{
				  "eventId": "%s",
				  "accountId": "%s",
				  "type": "%s",
				  "amount": %s,
				  "currency": "USD",
				  "eventTimestamp": "%s",
				  "metadata": {
				    "source": "test"
				  }
				}
				""".formatted(eventId, accountId, type, amount, eventTimestamp);
	}
}
