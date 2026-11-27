package com.epam.healenium.healenium_proxy.auth;

import com.epam.healenium.client.RestClient;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.WebSocket;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse.BodyHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Applies tenant and key headers to healenium-web {@link RestClient} calls (bypass Gateway).
 */
public final class RestClientTenantAuth {

    private RestClientTenantAuth() {
    }

    public static void apply(RestClient client, String tenantId) {
        applySession(client, tenantId, null);
    }

    /**
     * Sets {@code Healenium-Tenant-Id} and, when the session has a key, {@code Healenium-Key-Id} on every
     * call the client makes. A null or blank key id sends no key header.
     */
    public static void applySession(RestClient client, String tenantId, String keyId) {
        if (client == null) {
            return;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.hasText(tenantId)) {
            headers.put(TenantAuthGlobalFilter.TENANT_HEADER, tenantId.trim());
        }
        if (StringUtils.hasText(keyId)) {
            headers.put(TenantAuthGlobalFilter.KEY_ID_HEADER, keyId.trim());
        }
        if (!headers.isEmpty()) {
            wrap(client, headers);
        }
    }

    @Deprecated
    public static void apply(RestClient client, String tenantId, String internalToken) {
        apply(client, tenantId);
    }

    private static void wrap(RestClient client, Map<String, String> headers) {
        HttpClient serverClient = client.getServerHttpClient();
        if (serverClient != null) {
            client.setServerHttpClient(new HeaderInjectingHttpClient(serverClient, headers));
        }
        HttpClient aiClient = client.getAiServiceHttpClient();
        if (aiClient != null) {
            client.setAiServiceHttpClient(new HeaderInjectingHttpClient(aiClient, headers));
        }
    }

    private static final class HeaderInjectingHttpClient implements HttpClient {
        private final HttpClient delegate;
        private final Map<String, String> headers;

        private HeaderInjectingHttpClient(HttpClient delegate, Map<String, String> headers) {
            this.delegate = delegate;
            this.headers = headers;
        }

        private HttpRequest withHeader(HttpRequest request) {
            headers.forEach(request::setHeader);
            return request;
        }

        @Override
        public HttpResponse execute(HttpRequest request) throws UncheckedIOException {
            return delegate.execute(withHeader(request));
        }

        @Override
        public WebSocket openSocket(HttpRequest request, WebSocket.Listener listener) {
            return delegate.openSocket(withHeader(request), listener);
        }

        @Override
        public <T> CompletableFuture<java.net.http.HttpResponse<T>> sendAsyncNative(
                java.net.http.HttpRequest request, BodyHandler<T> handler) {
            return delegate.sendAsyncNative(request, handler);
        }

        @Override
        public <T> java.net.http.HttpResponse<T> sendNative(
                java.net.http.HttpRequest request, BodyHandler<T> handler)
                throws IOException, InterruptedException {
            return delegate.sendNative(request, handler);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
