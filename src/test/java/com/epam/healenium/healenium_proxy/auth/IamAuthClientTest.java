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
}
