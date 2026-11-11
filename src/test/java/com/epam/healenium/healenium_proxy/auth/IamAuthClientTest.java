package com.epam.healenium.healenium_proxy.auth;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IamAuthClientTest {

    private MockWebServer mockWebServer;
    private IamAuthClient iamAuthClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        iamAuthClient = new IamAuthClient(WebClient.builder(), baseUrl, 300, 1000);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testValidateApiKey_SuccessAndCacheHit() throws InterruptedException {
        UUID expectedTenantId = UUID.randomUUID();
        String jsonResponse = String.format("{\"valid\": true, \"tenantId\": \"%s\", \"role\": \"AUTOMATION\"}", expectedTenantId);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200)
                .setBody(jsonResponse));

        // 1. First call - network request to IAM
        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_validkey123"))
                .expectNext(expectedTenantId)
                .verifyComplete();

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/v1/auth/validate", recordedRequest.getPath());

        // 2. Second call with same key - cache hit! Zero additional requests
        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_validkey123"))
                .expectNext(expectedTenantId)
                .verifyComplete();

        assertEquals(1, mockWebServer.getRequestCount(), "Cache hit must not produce another network request");
    }

    @Test
    void testValidateBearerToken_SuccessAndCacheHit() throws InterruptedException {
        UUID expectedTenantId = UUID.randomUUID();
        String jsonResponse = String.format("{\"valid\": true, \"tenantId\": \"%s\", \"role\": \"ADMIN\"}", expectedTenantId);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200)
                .setBody(jsonResponse));

        // 1. First call
        StepVerifier.create(iamAuthClient.validateBearerToken("mock_bearer_jwt_token"))
                .expectNext(expectedTenantId)
                .verifyComplete();

        assertEquals(1, mockWebServer.getRequestCount());

        // 2. Second call - cached
        StepVerifier.create(iamAuthClient.validateBearerToken("mock_bearer_jwt_token"))
                .expectNext(expectedTenantId)
                .verifyComplete();

        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void testValidateApiKey_Invalid_ReturnsEmpty() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(401)
                .setBody("{\"valid\": false, \"errorMessage\": \"Invalid key\"}"));

        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_invalidkey"))
                .verifyComplete();
    }

    @Test
    void testValidateBearerToken_ServerError_ReturnsEmpty() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal server error"));

        StepVerifier.create(iamAuthClient.validateBearerToken("bad_token"))
                .verifyComplete();
    }

    @Test
    void testValidateBearerToken_CacheEntryCappedAtJwtExp() {
        Instant start = Instant.parse("2026-11-11T12:00:00Z");
        AtomicReference<Instant> currentTime = new AtomicReference<>(start);
        Clock mutableClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };

        String baseUrl = mockWebServer.url("/").toString();
        IamAuthClient client = new IamAuthClient(WebClient.builder(), baseUrl, 300, 1000, mutableClock);

        long expSeconds = start.plusSeconds(10).getEpochSecond();
        String jwt = buildUnsignedJwtWithExp(expSeconds);
        UUID expectedTenantId = UUID.randomUUID();
        String okBody = String.format("{\"valid\": true, \"tenantId\": \"%s\", \"role\": \"MEMBER\"}", expectedTenantId);

        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200)
                .setBody(okBody));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(401)
                .setBody("{\"valid\": false, \"errorMessage\": \"Token expired\"}"));

        // 1. At t=0, validated via IAM and cached
        StepVerifier.create(client.validateBearerToken(jwt))
                .expectNext(expectedTenantId)
                .verifyComplete();
        assertEquals(1, mockWebServer.getRequestCount());

        // 2. At t=+5s (before exp=+10s), served from cache
        currentTime.set(start.plusSeconds(5));
        StepVerifier.create(client.validateBearerToken(jwt))
                .expectNext(expectedTenantId)
                .verifyComplete();
        assertEquals(1, mockWebServer.getRequestCount());

        // 3. At t=+10s (at/after exp, well before 300s cache TTL), cache entry must be expired
        currentTime.set(start.plusSeconds(10));
        StepVerifier.create(client.validateBearerToken(jwt))
                .verifyComplete();
        assertEquals(2, mockWebServer.getRequestCount());
    }

    private static String buildUnsignedJwtWithExp(long expEpochSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"user1\",\"exp\":" + expEpochSeconds + "}")
                        .getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
