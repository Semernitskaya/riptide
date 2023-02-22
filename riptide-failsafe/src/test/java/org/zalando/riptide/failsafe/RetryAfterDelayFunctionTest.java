package org.zalando.riptide.failsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.restdriver.clientdriver.ClientDriver;
import com.github.restdriver.clientdriver.ClientDriverFactory;
import com.google.common.base.Stopwatch;
import dev.failsafe.CircuitBreaker;
import dev.failsafe.RetryPolicy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.zalando.riptide.Http;
import org.zalando.riptide.httpclient.ApacheClientHttpRequestFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import static com.github.restdriver.clientdriver.RestClientDriver.giveEmptyResponse;
import static com.github.restdriver.clientdriver.RestClientDriver.onRequestTo;
import static java.time.Instant.parse;
import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.springframework.http.HttpStatus.Series.SUCCESSFUL;
import static org.zalando.riptide.Bindings.anySeries;
import static org.zalando.riptide.Bindings.on;
import static org.zalando.riptide.Navigators.series;
import static org.zalando.riptide.Navigators.status;
import static org.zalando.riptide.PassRoute.pass;
import static org.zalando.riptide.failsafe.RetryRoute.retry;

final class RetryAfterDelayFunctionTest {

    private final ClientDriver driver = new ClientDriverFactory().createClientDriver();

    private final CloseableHttpClient client = HttpClientBuilder.create()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setSocketTimeout(1000)
                    .build())
            .build();

    private final Clock clock = Clock.fixed(parse("2018-04-11T22:34:27Z"), UTC);

    private final Http unit = Http.builder()
            .executor(newSingleThreadExecutor())
            .requestFactory(new ApacheClientHttpRequestFactory(client))
            .baseUrl(driver.getBaseUrl())
            .converter(createJsonConverter())
            .plugin(new FailsafePlugin()
                    .withPolicy(CircuitBreaker.<ClientHttpResponse>builder().build())
                    .withPolicy(RetryPolicy.<ClientHttpResponse>builder()
                            .withDelay(Duration.ofSeconds(2))
                            // TODO: check delay
                            //.withDelay(new RetryAfterDelayFunction(clock))
                            .withMaxDuration(Duration.ofSeconds(5))
                            .withMaxRetries(4)
                            .build()))
            .build();

    private static MappingJackson2HttpMessageConverter createJsonConverter() {
        final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(createObjectMapper());
        return converter;
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void shouldRetryWithoutDynamicDelay() {
        driver.addExpectation(onRequestTo("/baz"), giveEmptyResponse().withStatus(503));
        driver.addExpectation(onRequestTo("/baz"), giveEmptyResponse());

        unit.get("/baz")
                .dispatch(series(),
                        on(SUCCESSFUL).call(pass()),
                        anySeries().dispatch(status(),
                                on(HttpStatus.SERVICE_UNAVAILABLE).call(retry())))
                .join();
    }

    @Test
    void shouldIgnoreDynamicDelayOnInvalidFormatAndRetryImmediately() {
        driver.addExpectation(onRequestTo("/baz"), giveEmptyResponse().withStatus(503)
                .withHeader("Retry-After", "foo"));
        driver.addExpectation(onRequestTo("/baz"), giveEmptyResponse());

        unit.get("/baz")
                .dispatch(series(),
                        on(SUCCESSFUL).call(pass()),
                        anySeries().dispatch(status(),
                                on(HttpStatus.SERVICE_UNAVAILABLE).call(retry())))
                .join();
    }

    @Test
    void shouldRetryOnDemandWithDynamicDelay() {
        driver.addExpectation(onRequestTo("/baz"), giveEmptyResponse().withStatus(503)
                .withHeader("Retry-After", "1"));
        driver.addExpectation(onRequestTo("/baz"), giveEmptyResponse());

        assertTimeout(Duration.ofMillis(1500), () ->
                atLeast(Duration.ofSeconds(1), () -> unit.get("/baz")
                        .dispatch(series(),
                                on(SUCCESSFUL).call(pass()),
                                anySeries().dispatch(status(),
                                        on(HttpStatus.SERVICE_UNAVAILABLE).call(retry())))
                        .join()));
    }

    @Test
    void shouldRetryWithDynamicDelayDate() {
        driver.addExpectation(onRequestTo("/baz"), giveEmptyResponse().withStatus(503)
                .withHeader("Retry-After", "Wed, 11 Apr 2018 22:34:28 GMT"));
        driver.addExpectation(onRequestTo("/baz"), giveEmptyResponse());

        assertTimeout(Duration.ofMillis(2000), () ->
                atLeast(Duration.ofSeconds(1), () -> unit.get("/baz")
                        .dispatch(series(),
                                on(SUCCESSFUL).call(pass()))
                        .join()));
    }

    private void atLeast(final Duration minimum, final Runnable runnable) {
        final Duration actual = time(runnable);

        assertThat(actual, greaterThanOrEqualTo(minimum));
    }

    private Duration time(final Runnable runnable) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        runnable.run();
        return stopwatch.stop().elapsed();
    }

}
