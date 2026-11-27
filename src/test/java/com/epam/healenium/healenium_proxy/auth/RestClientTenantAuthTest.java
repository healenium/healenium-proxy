package com.epam.healenium.healenium_proxy.auth;

import com.epam.healenium.client.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestClientTenantAuthTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String KEY = "22222222-2222-2222-2222-222222222222";

    @Test
    void aSessionWithAKey_setsTenantAndKeyOnServerAndAiCalls() {
        RestClient client = mock(RestClient.class);
        HttpClient server = mock(HttpClient.class);
        HttpClient ai = mock(HttpClient.class);
        when(client.getServerHttpClient()).thenReturn(server);
        when(client.getAiServiceHttpClient()).thenReturn(ai);

        RestClientTenantAuth.applySession(client, TENANT, KEY);

        ArgumentCaptor<HttpClient> wrappedServer = ArgumentCaptor.forClass(HttpClient.class);
        ArgumentCaptor<HttpClient> wrappedAi = ArgumentCaptor.forClass(HttpClient.class);
        verify(client).setServerHttpClient(wrappedServer.capture());
        verify(client).setAiServiceHttpClient(wrappedAi.capture());

        wrappedServer.getValue().execute(new HttpRequest(HttpMethod.POST, "/healenium/healing"));
        wrappedAi.getValue().execute(new HttpRequest(HttpMethod.POST, "/healenium-ai/selectors"));

        ArgumentCaptor<HttpRequest> sentServer = ArgumentCaptor.forClass(HttpRequest.class);
        verify(server).execute(sentServer.capture());
        assertEquals(TENANT, sentServer.getValue().getHeader(TenantAuthGlobalFilter.TENANT_HEADER));
        assertEquals(KEY, sentServer.getValue().getHeader(TenantAuthGlobalFilter.KEY_ID_HEADER));

        ArgumentCaptor<HttpRequest> sentAi = ArgumentCaptor.forClass(HttpRequest.class);
        verify(ai).execute(sentAi.capture());
        assertEquals(KEY, sentAi.getValue().getHeader(TenantAuthGlobalFilter.KEY_ID_HEADER));
    }

    @Test
    void aTenantOnlySession_sendsTheTenantAndNoKeyHeader() {
        RestClient client = mock(RestClient.class);
        HttpClient server = mock(HttpClient.class);
        when(client.getServerHttpClient()).thenReturn(server);

        RestClientTenantAuth.applySession(client, TENANT, null);

        ArgumentCaptor<HttpClient> wrapped = ArgumentCaptor.forClass(HttpClient.class);
        verify(client).setServerHttpClient(wrapped.capture());
        wrapped.getValue().execute(new HttpRequest(HttpMethod.POST, "/healenium/healing"));

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(server).execute(sent.capture());
        assertEquals(TENANT, sent.getValue().getHeader(TenantAuthGlobalFilter.TENANT_HEADER));
        assertNull(sent.getValue().getHeader(TenantAuthGlobalFilter.KEY_ID_HEADER));
    }
}
