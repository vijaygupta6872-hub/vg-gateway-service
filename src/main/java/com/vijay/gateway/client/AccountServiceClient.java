package com.vijay.gateway.client;

import com.vijay.gateway.trace.TraceConstants;
import com.vijay.gateway.exception.AccountServiceUnavailableException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
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

/**
 * HTTP client for the downstream Account Service transaction API.
 * <p>
 * The Gateway uses this client to apply new events to an account before
 * persisting them locally. Calls are protected by the {@code accountService}
 * circuit breaker and include the current {@code X-Trace-Id} value when one is
 * available in the logging MDC. Transport failures, timeouts, open circuit
 * failures, and downstream {@code 5xx} responses are normalized to
 * {@link AccountServiceUnavailableException} so the API layer can return a
 * consistent {@code 503 Service Unavailable} response.
 * </p>
 */
@Component
public class AccountServiceClient {

	private static final String CIRCUIT_BREAKER_NAME = "accountService";

	private final RestClient restClient;
	private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

	/**
	 * Creates a client configured from application properties.
	 *
	 * @param restClientBuilder Spring-managed builder used to create the underlying
	 *                          {@link RestClient}
	 * @param circuitBreakerFactory factory used to create the {@code accountService}
	 *                              circuit breaker
	 * @param baseUrl base URL of Account Service
	 * @param connectTimeout maximum time allowed to establish the HTTP connection
	 * @param readTimeout maximum time allowed to wait for the HTTP response
	 * @throws ArithmeticException if a configured timeout cannot be represented as
	 *         an {@code int} millisecond value
	 */
	public AccountServiceClient(
			RestClient.Builder restClientBuilder,
			CircuitBreakerFactory<?, ?> circuitBreakerFactory,
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
		this.circuitBreakerFactory = circuitBreakerFactory;
	}

	/**
	 * Posts an account transaction for a Gateway event.
	 * <p>
	 * The current trace identifier is captured before entering the circuit breaker
	 * so it can be propagated even if the circuit breaker changes execution
	 * context.
	 * </p>
	 *
	 * @param accountId account identifier used in the Account Service URL path
	 * @param request transaction payload derived from the Gateway event request
	 * @throws AccountServiceUnavailableException if Account Service cannot be
	 *         reached, times out, returns a {@code 5xx} response, or the circuit
	 *         breaker is open
	 * @throws RestClientResponseException if Account Service returns a non-5xx HTTP
	 *         error response
	 */
	public void postTransaction(String accountId, AccountTransactionRequest request) {
		String traceId = currentTraceId();
		circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME).run(
				() -> {
					postTransactionInternal(accountId, request, traceId);
					return null;
				},
				throwable -> {
					if (throwable instanceof AccountServiceUnavailableException unavailableException) {
						throw unavailableException;
					}
					if (throwable instanceof RestClientResponseException responseException
							&& !responseException.getStatusCode().is5xxServerError()) {
						throw responseException;
					}
					throw unavailable("Account Service is unavailable", throwable);
				}
		);
	}

	/**
	 * Executes the actual HTTP POST to Account Service.
	 *
	 * @param accountId account identifier used to expand the transaction URL
	 * @param request transaction payload to serialize as JSON
	 * @param traceId trace identifier to propagate through the {@code X-Trace-Id}
	 *                request header; may be {@code null} or blank
	 * @throws AccountServiceUnavailableException if a transport failure, timeout,
	 *         or Account Service {@code 5xx} response occurs
	 * @throws RestClientResponseException if Account Service returns a non-5xx HTTP
	 *         error response
	 */
	private void postTransactionInternal(String accountId, AccountTransactionRequest request, String traceId) {
		try {
			restClient.post()
					.uri("/accounts/{accountId}/transactions", accountId)
					.headers(headers -> {
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

	/**
	 * Returns the trace identifier currently stored in MDC.
	 *
	 * @return trace identifier associated with the current request, or {@code null}
	 *         if no request trace is active
	 */
	private static String currentTraceId() {
		return MDC.get(TraceConstants.MDC_TRACE_ID);
	}

	/**
	 * Creates the normalized exception used for unavailable Account Service states.
	 *
	 * @param message business-readable failure message
	 * @param cause original exception that caused the unavailable state
	 * @return exception to propagate to the service/API layer
	 */
	private static AccountServiceUnavailableException unavailable(String message, Throwable cause) {
		return new AccountServiceUnavailableException(message, cause);
	}

	/**
	 * Converts a timeout duration to milliseconds for the request factory.
	 *
	 * @param timeout configured timeout duration
	 * @return timeout value in milliseconds
	 * @throws ArithmeticException if the duration exceeds the supported
	 *         {@code int} millisecond range
	 */
	private static int timeoutMillis(Duration timeout) {
		return Math.toIntExact(timeout.toMillis());
	}
}
