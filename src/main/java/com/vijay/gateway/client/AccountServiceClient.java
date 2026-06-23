package com.vijay.gateway.client;

import com.vijay.gateway.trace.TraceConstants;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
public class AccountServiceClient {

	private final RestClient restClient;

	public AccountServiceClient(
			RestClient.Builder restClientBuilder,
			@Value("${account-service.base-url}") String baseUrl,
			@Value("${account-service.connect-timeout:2s}") Duration connectTimeout,
			@Value("${account-service.read-timeout:3s}") Duration readTimeout
	) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(timeoutMillis(connectTimeout));
		requestFactory.setReadTimeout(timeoutMillis(readTimeout));

		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.build();
	}

	public void postTransaction(String accountId, AccountTransactionRequest request) {
		try {
			restClient.post()
					.uri("/accounts/{accountId}/transactions", accountId)
					.headers(headers -> {
						String traceId = currentTraceId();
						if (StringUtils.hasText(traceId)) {
							headers.set(TraceConstants.X_TRACE_ID, traceId);
						}
					})
					.body(request)
					.retrieve()
					.toBodilessEntity();
		} catch (ResourceAccessException ex) {
			throw unavailable("Account Service is unavailable", ex);
		} catch (HttpServerErrorException ex) {
			throw unavailable("Account Service returned a server error", ex);
		} catch (RestClientResponseException ex) {
			if (ex.getStatusCode().is5xxServerError()) {
				throw unavailable("Account Service returned a server error", ex);
			}
			throw ex;
		}
	}

	private static String currentTraceId() {
		return MDC.get(TraceConstants.MDC_TRACE_ID);
	}

	private static AccountServiceUnavailableException unavailable(String message, Throwable cause) {
		return new AccountServiceUnavailableException(message, cause);
	}

	private static int timeoutMillis(Duration timeout) {
		return Math.toIntExact(timeout.toMillis());
	}
}
