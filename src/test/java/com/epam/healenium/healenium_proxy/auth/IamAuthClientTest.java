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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        // 1. First call - network request to IAM's internal endpoint
        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_validkey123"))
                .expectNext(expectedTenantId)
                .verifyComplete();

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("/internal/auth/validate", recordedRequest.getPath());

        // 2. Second call with same key - cache hit! Zero additional requests
        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_validkey123"))
                .expectNext(expectedTenantId)
                .verifyComplete();

        assertEquals(1, mockWebServer.getRequestCount(), "Cache hit must not produce another network request");
    }

    @Test
    void evictApiKey_forcesTheNextCallBackToIam() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String valid = String.format("{\"valid\": true, \"tenantId\": \"%s\", \"role\": \"AUTOMATION\"}", tenantId);
        for (int i = 0; i < 2; i++) {
            mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(valid));
        }
        String rawKey = "hlm_live_revoked-soon";
        StepVerifier.create(iamAuthClient.validateApiKey(rawKey)).expectNext(tenantId).verifyComplete();
        StepVerifier.create(iamAuthClient.validateApiKey(rawKey)).expectNext(tenantId).verifyComplete();
        assertEquals(1, mockWebServer.getRequestCount(), "the second call is served from the cache");

        iamAuthClient.evictApiKey(sha256Hex(rawKey));

        StepVerifier.create(iamAuthClient.validateApiKey(rawKey)).expectNext(tenantId).verifyComplete();
        assertEquals(2, mockWebServer.getRequestCount(), "after the eviction the key is validated again");
    }

    @Test
    void evictApiKey_leavesOtherKeysCached() {
        UUID tenantId = UUID.randomUUID();
        String valid = String.format("{\"valid\": true, \"tenantId\": \"%s\", \"role\": \"AUTOMATION\"}", tenantId);
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(valid));
        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_keep-me")).expectNext(tenantId).verifyComplete();

        iamAuthClient.evictApiKey(sha256Hex("hlm_live_some-other-key"));

        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_keep-me")).expectNext(tenantId).verifyComplete();
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void personalKey_isCachedForTheSmallerOfCacheSecondsAndTheProxyTtl() {
        MutableClock clock = new MutableClock();
        IamAuthClient client = new IamAuthClient(WebClient.builder(), mockWebServer.url("/").toString(), 300, 1000, clock);
        enqueueValid(UUID.randomUUID(), UUID.randomUUID(), 60);
        enqueueValid(UUID.randomUUID(), UUID.randomUUID(), 60);

        StepVerifier.create(client.validateApiKeyDetails("hlm_live_personal")).expectNextCount(1).verifyComplete();
        clock.advanceSeconds(59);
        StepVerifier.create(client.validateApiKeyDetails("hlm_live_personal")).expectNextCount(1).verifyComplete();
        assertEquals(1, mockWebServer.getRequestCount(), "inside cacheSeconds the entry is served from the cache");

        clock.advanceSeconds(2);
        StepVerifier.create(client.validateApiKeyDetails("hlm_live_personal")).expectNextCount(1).verifyComplete();
        assertEquals(2, mockWebServer.getRequestCount(), "after cacheSeconds the key is validated again");
    }

    @Test
    void personalKey_withCacheSecondsAboveTheProxyTtl_isCachedForTheProxyTtl() {
        MutableClock clock = new MutableClock();
        IamAuthClient client = new IamAuthClient(WebClient.builder(), mockWebServer.url("/").toString(), 300, 1000, clock);
        enqueueValid(UUID.randomUUID(), UUID.randomUUID(), 600);
        enqueueValid(UUID.randomUUID(), UUID.randomUUID(), 600);

        StepVerifier.create(client.validateApiKeyDetails("hlm_live_long")).expectNextCount(1).verifyComplete();
        clock.advanceSeconds(299);
        StepVerifier.create(client.validateApiKeyDetails("hlm_live_long")).expectNextCount(1).verifyComplete();
        assertEquals(1, mockWebServer.getRequestCount());

        clock.advanceSeconds(2);
        StepVerifier.create(client.validateApiKeyDetails("hlm_live_long")).expectNextCount(1).verifyComplete();
        assertEquals(2, mockWebServer.getRequestCount(), "the proxy TTL caps the entry, not cacheSeconds");
    }

    @Test
    void projectKey_withoutCacheSeconds_keepsTheProxyTtl_andCachesTheKeyId() {
        MutableClock clock = new MutableClock();
        IamAuthClient client = new IamAuthClient(WebClient.builder(), mockWebServer.url("/").toString(), 300, 1000, clock);
        UUID keyId = UUID.randomUUID();
        enqueueValid(UUID.randomUUID(), keyId, null);

        StepVerifier.create(client.validateApiKeyDetails("hlm_live_project"))
                .assertNext(auth -> assertEquals(keyId, auth.keyId()))
                .verifyComplete();
        clock.advanceSeconds(299);
        StepVerifier.create(client.validateApiKeyDetails("hlm_live_project"))
                .assertNext(auth -> assertEquals(keyId, auth.keyId(), "the cached result keeps the key id"))
                .verifyComplete();
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void aKeyWithoutKeyId_isCachedWithANullKeyId() {
        UUID tenantId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody(String.format("{\"valid\": true, \"tenantId\": \"%s\", \"role\": \"AUTOMATION\"}", tenantId)));

        StepVerifier.create(iamAuthClient.validateApiKeyDetails("hlm_live_old-iam"))
                .assertNext(auth -> {
                    assertEquals(tenantId, auth.tenantId());
                    assertNull(auth.keyId());
                })
                .verifyComplete();
    }

    private void enqueueValid(UUID tenantId, UUID keyId, Integer cacheSeconds) {
        String body = String.format("{\"valid\": true, \"tenantId\": \"%s\", \"role\": \"AUTOMATION\", \"keyId\": \"%s\"%s}",
                tenantId, keyId, cacheSeconds == null ? "" : ", \"cacheSeconds\": " + cacheSeconds);
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static String sha256Hex(String input) throws RuntimeException {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
    void testValidateApiKey_ServerError_ReturnsEmpty() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal server error"));

        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_any"))
                .verifyComplete();
    }
}
