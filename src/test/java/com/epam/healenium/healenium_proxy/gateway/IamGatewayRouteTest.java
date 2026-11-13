package com.epam.healenium.healenium_proxy.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IamGatewayRouteTest {

    private static MockWebServer mockIamServer;

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeAll
    static void startServer() throws IOException {
        mockIamServer = new MockWebServer();
        mockIamServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (mockIamServer != null) {
            mockIamServer.shutdown();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("proxy.iam.url", () -> mockIamServer.url("/").toString());
    }

    @BeforeEach
    void setUpClient() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void testIamRouteRewritesAndProxiesToV1() throws InterruptedException {
        mockIamServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200)
                .setBody("{\"status\": \"ok\"}"));

        webTestClient.get()
                .uri("/iam/users/me")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok");

        RecordedRequest recorded = mockIamServer.takeRequest();
        assertEquals("/v1/users/me", recorded.getPath());
        assertEquals("Bearer test-token", recorded.getHeader("Authorization"));
    }
}
