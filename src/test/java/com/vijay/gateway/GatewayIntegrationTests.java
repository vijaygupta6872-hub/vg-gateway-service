package com.vijay.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vijay.gateway.repository.EventRecordRepository;
import com.vijay.gateway.trace.TraceConstants;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayIntegrationTests {

	private static MockWebServer accountService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EventRecordRepository eventRecordRepository;

	@BeforeAll
	static void startAccountService() throws IOException {
		accountService = new MockWebServer();
		accountService.start();
	}

	@AfterAll
	static void stopAccountService() throws IOException {
		accountService.close();
	}

	@DynamicPropertySource
	static void accountServiceProperties(DynamicPropertyRegistry registry) {
		registry.add("account-service.base-url", () -> accountService.url("/").toString());
	}

	@BeforeEach
	void setUp() {
		eventRecordRepository.deleteAll();
	}

	@Test
	void postEventsCallsAccountServicePropagatesTraceIdPersistsAfterSuccessAndReturnsCreated() throws Exception {
		accountService.enqueue(new MockResponse.Builder()
				.code(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.body("{}")
				.build());

		mockMvc.perform(post("/events")
						.header(TraceConstants.X_TRACE_ID, "trace-integration-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "eventId": "event-1",
								  "accountId": "account-1",
								  "type": "CREDIT",
								  "amount": 25.50,
								  "currency": "USD",
								  "eventTimestamp": "2026-06-22T10:00:00Z",
								  "metadata": {
								    "source": "integration-test"
								  }
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string(TraceConstants.X_TRACE_ID, "trace-integration-1"))
				.andExpect(jsonPath("$.eventId").value("event-1"))
				.andExpect(jsonPath("$.status").value("APPLIED"));

		RecordedRequest accountRequest = accountService.takeRequest(1, TimeUnit.SECONDS);
		assertThat(accountRequest).isNotNull();
		assertThat(accountRequest.getMethod()).isEqualTo("POST");
		assertThat(accountRequest.getUrl().encodedPath()).isEqualTo("/accounts/account-1/transactions");
		assertThat(accountRequest.getHeaders().get(TraceConstants.X_TRACE_ID)).isEqualTo("trace-integration-1");

		JsonNode accountRequestBody = objectMapper.readTree(accountRequest.getBody().utf8());
		assertThat(accountRequestBody.get("eventId").asText()).isEqualTo("event-1");
		assertThat(accountRequestBody.get("type").asText()).isEqualTo("CREDIT");
		assertThat(accountRequestBody.get("amount").decimalValue()).isEqualByComparingTo("25.50");
		assertThat(accountRequestBody.get("metadata").get("source").asText()).isEqualTo("integration-test");

		assertThat(eventRecordRepository.findByEventId("event-1")).isPresent();
	}
}
