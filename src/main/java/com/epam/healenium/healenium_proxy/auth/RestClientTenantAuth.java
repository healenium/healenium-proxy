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
import java.util.concurrent.CompletableFuture;

/**
 * Applies tenant + M2M headers to healenium-web {@link RestClient} calls (bypass Gateway).
 */
public final class RestClientTenantAuth {

    private RestClientTenantAuth() {
    }

    public static void apply(RestClient client, String tenantId, String internalToken) {
        if (client == null) {
            return;
        }
        // healenium-web RestClient still sets legacy X-Tenant-Id via setTenantId; also inject RFC6648 name.
        client.setTenantId(tenantId);
        if (StringUtils.hasText(tenantId)) {
            wrap(client, TenantAuthGlobalFilter.TENANT_HEADER, tenantId.trim());
        }
        if (StringUtils.hasText(internalToken)) {
            wrap(client, TenantAuthGlobalFilter.INTERNAL_TOKEN_HEADER, internalToken.trim());
        }
    }

    private static void wrap(RestClient client, String headerName, String headerValue) {
        HttpClient current = client.getServerHttpClient();
        if (current == null) {
            return;
        }
        client.setServerHttpClient(new HeaderInjectingHttpClient(current, headerName, headerValue));
    }

    private static final class HeaderInjectingHttpClient implements HttpClient {
        private final HttpClient delegate;
        private final String headerName;
        private final String headerValue;

        private HeaderInjectingHttpClient(HttpClient delegate, String headerName, String headerValue) {
            this.delegate = delegate;
            this.headerName = headerName;
            this.headerValue = headerValue;
        }

        private HttpRequest withHeader(HttpRequest request) {
            request.setHeader(headerName, headerValue);
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
