package com.epam.healenium.healenium_proxy.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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

/**
 * IAM endpoints moved to hlm-iam (spec 004), so this proxy no longer routes {@code /healenium-iam/**}.
 * In pro mode (auth enabled) such a path must answer 404 and must never reach hlm-iam or Selenium.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "healenium.auth.enabled=true")
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
    void iamPath_answers404_andIsNotProxiedToIam() {
        mockIamServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(200)
                .setBody("{\"status\": \"ok\"}"));

        webTestClient.get()
                .uri("/healenium-iam/users/me")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();

        assertEquals(0, mockIamServer.getRequestCount());
    }
}
