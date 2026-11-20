package com.epam.healenium.healenium_proxy.auth;

import com.epam.healenium.client.RestClient;
import com.epam.healenium.model.RequestDto;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.WebSocket;
import org.springframework.http.HttpMethod;

import java.net.http.HttpResponse.BodyHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-004: every request that the SDK's {@link RestClient} sends must be on {@link SdkEndpoints}.
 * The client is driven through a capturing HTTP client, so the paths it really builds are checked.
 *
 * <p>Not covered here, by design of their inputs: {@code healRequest} needs a populated healing
 * {@code Context} and {@code getXpathSelector} needs a DOM {@code Node}. Their paths are
 * {@code POST /healenium/healing} and {@code POST /healenium-ai/selectors/xpath}, which are on the
 * allow-list and are checked by {@link #allowListCoversTheSdkPaths()}.
 */
class SdkEndpointsCoverageTest {

    private final List<HttpRequest> sent = new ArrayList<>();

    @Test
    void everyRequestTheSdkSends_isOnTheAllowList() {
        RestClient client = new RestClient(ConfigFactory.parseString(
                "sessionKey = \"s\"\n"
                        + "hlm.server.url = \"http://hlm-proxy:8085\"\n"
                        + "hlm.imitator.url = \"http://imitator:8000\"\n"
                        + "hlm.ai.url = \"http://hlm-proxy:8085\"\n"));
        CapturingHttpClient capturing = new CapturingHttpClient(sent);
        client.setServerHttpClient(capturing);
        client.setAiServiceHttpClient(capturing);

        client.saveElements(new RequestDto());
        client.getElements();
        client.getReferenceElements(By.id("x"), "click", "http://page");
        client.initReport("session-1");
        client.getTenantSettings();

        assertFalse(sent.isEmpty(), "the capturing client must see the SDK calls");
        for (HttpRequest request : sent) {
            // The SDK's server client is relative to its /healenium base; replacing the client drops
            // that base, so it is added back here to compare the full path that hlm-proxy receives.
            String fullPath = "/healenium" + request.getUri();
            assertTrue(SdkEndpoints.isAllowed(HttpMethod.valueOf(request.getMethod().name()), fullPath),
                    () -> "SDK sent a call that hlm-proxy does not allow: " + request.getMethod() + " " + fullPath);
        }
    }

    @Test
    void allowListCoversTheSdkPaths() {
        assertTrue(SdkEndpoints.isAllowed(HttpMethod.POST, "/healenium"));
        assertTrue(SdkEndpoints.isAllowed(HttpMethod.GET, "/healenium"));
        assertTrue(SdkEndpoints.isAllowed(HttpMethod.GET, "/healenium/elements"));
        assertTrue(SdkEndpoints.isAllowed(HttpMethod.POST, "/healenium/healing"));
        assertTrue(SdkEndpoints.isAllowed(HttpMethod.POST, "/healenium/report/init/abc"));
        assertTrue(SdkEndpoints.isAllowed(HttpMethod.GET, "/healenium/settings"));
        assertTrue(SdkEndpoints.isAllowed(HttpMethod.POST, "/healenium-ai/selectors/xpath"));
    }

    @Test
    void allowListRejectsEverythingElse() {
        assertFalse(SdkEndpoints.isAllowed(HttpMethod.GET, "/healenium/report/all"));
        assertFalse(SdkEndpoints.isAllowed(HttpMethod.GET, "/healenium/logs/time-range"));
        assertFalse(SdkEndpoints.isAllowed(HttpMethod.GET, "/healenium/actuator/health"));
        assertFalse(SdkEndpoints.isAllowed(HttpMethod.DELETE, "/healenium/elements"));
        assertFalse(SdkEndpoints.isAllowed(HttpMethod.POST, "/healenium/report/init/"));
        assertFalse(SdkEndpoints.isAllowed(HttpMethod.POST, "/healenium/report/init/a/b"));
        assertFalse(SdkEndpoints.isAllowed(HttpMethod.POST, "/hlm-proxy/settings/update"));
    }

    /** Answers every call with an empty JSON object, and records the request. */
    private static final class CapturingHttpClient implements HttpClient {
        private final List<HttpRequest> sink;

        CapturingHttpClient(List<HttpRequest> sink) {
            this.sink = sink;
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            sink.add(request);
            return new HttpResponse().setStatus(200).setContent(Contents.utf8String("{}"));
        }

        @Override
        public WebSocket openSocket(HttpRequest request, WebSocket.Listener listener) {
            throw new UnsupportedOperationException("the SDK does not open sockets to the proxy");
        }

        @Override
        public <T> CompletableFuture<java.net.http.HttpResponse<T>> sendAsyncNative(
                java.net.http.HttpRequest request, BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.net.http.HttpResponse<T> sendNative(java.net.http.HttpRequest request, BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
