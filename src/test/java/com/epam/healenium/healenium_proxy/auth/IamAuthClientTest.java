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
import java.util.UUID;

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

    @Test
    void sendsTheInternalSecretHeaderWhenConfigured() throws InterruptedException {
        IamAuthClient withSecret = new IamAuthClient(WebClient.builder(), mockWebServer.url("/").toString(), 300, 1000,
                java.time.Clock.systemUTC(), "shared-secret");
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(401)
                .setBody("{\"valid\": false}"));

        StepVerifier.create(withSecret.validateApiKey("hlm_live_any")).verifyComplete();

        assertEquals("shared-secret", mockWebServer.takeRequest().getHeader("X-Internal-Secret"));
    }

    @Test
    void sendsNoInternalSecretHeaderWhenNotConfigured() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setResponseCode(401)
                .setBody("{\"valid\": false}"));

        StepVerifier.create(iamAuthClient.validateApiKey("hlm_live_any")).verifyComplete();

        org.junit.jupiter.api.Assertions.assertNull(mockWebServer.takeRequest().getHeader("X-Internal-Secret"));
    }
}
