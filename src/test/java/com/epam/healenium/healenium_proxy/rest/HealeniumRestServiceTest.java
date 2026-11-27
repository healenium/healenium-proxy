package com.epam.healenium.healenium_proxy.rest;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.epam.healenium.healenium_proxy.auth.TenantAuthGlobalFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HealeniumRestServiceTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String KEY = "22222222-2222-2222-2222-222222222222";

    private MockWebServer backend;
    private HealeniumRestService service;

    @BeforeEach
    void setUp() throws IOException {
        backend = new MockWebServer();
        backend.start();
        service = new HealeniumRestService();
        ReflectionTestUtils.setField(service, "healeniumContainerUrl", backend.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        backend.shutdown();
    }

    @Test
    void sessionInit_withAKey_sendsTheKeyIdNextToTheTenant() throws Exception {
        backend.enqueue(new MockResponse().setResponseCode(200));

        service.restoreSessionOnServer(URI.create("http://127.0.0.1:4444").toURL(), "session-1", Map.of(), TENANT, KEY);

        RecordedRequest request = backend.takeRequest();
        assertEquals("/healenium/session", request.getPath());
        assertEquals(TENANT, request.getHeader(TenantAuthGlobalFilter.TENANT_HEADER));
        assertEquals(KEY, request.getHeader(TenantAuthGlobalFilter.KEY_ID_HEADER));
    }

    @Test
    void sessionInit_withoutAKey_sendsNoKeyHeader() throws Exception {
        backend.enqueue(new MockResponse().setResponseCode(200));

        service.restoreSessionOnServer(URI.create("http://127.0.0.1:4444").toURL(), "session-2", Map.of(), TENANT, null);

        RecordedRequest request = backend.takeRequest();
        assertEquals(TENANT, request.getHeader(TenantAuthGlobalFilter.TENANT_HEADER));
        assertNull(request.getHeader(TenantAuthGlobalFilter.KEY_ID_HEADER));
    }
}
