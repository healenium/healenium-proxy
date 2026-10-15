package com.epam.healenium.healenium_proxy.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAuthGlobalFilterTest {

    @Mock
    private IamAuthClient iamAuthClient;
    @Mock
    private GatewayFilterChain chain;

    private HealeniumAuthProperties properties;
    private TenantAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new HealeniumAuthProperties();
        filter = new TenantAuthGlobalFilter(iamAuthClient, properties);
    }

    @Test
    void testNonBackendPathIgnored() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/wd/hub/status").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(iamAuthClient);
    }

    @Test
    void testAuthDisabledAllowsBackendPathWithoutAuth() {
        properties.getAuth().setEnabled(false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/healenium/elements").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(iamAuthClient);
    }

    @Test
    void testAuthEnabledMissingAuthHeaderRejects401() {
        properties.getAuth().setEnabled(true);
        MockServerHttpRequest request = MockServerHttpRequest.get("/healenium/elements").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    void testAuthEnabledInvalidTokenRejects401() {
        properties.getAuth().setEnabled(true);
        when(iamAuthClient.validateBearerToken("invalid_jwt")).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/healenium/elements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid_jwt")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    void testAuthEnabledValidTokenMutatesHeadersAndCallsChain() {
        properties.getAuth().setEnabled(true);
        UUID expectedTenantId = UUID.randomUUID();
        when(iamAuthClient.validateBearerToken("valid_jwt")).thenReturn(Mono.just(expectedTenantId));
        when(chain.filter(any())).thenReturn(Mono.empty());

        // Spoofing attempt: client passes a forged Healenium-Tenant-Id and legacy Healenium-Internal-Token
        MockServerHttpRequest request = MockServerHttpRequest.get("/healenium/elements")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_jwt")
                .header("Healenium-Tenant-Id", UUID.randomUUID().toString())
                .header("Healenium-Internal-Token", "forged_secret")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange downstreamExchange = captor.getValue();
        HttpHeaders downstreamHeaders = downstreamExchange.getRequest().getHeaders();

        // 1. Authorization header stripped
        assertNull(downstreamHeaders.getFirst(HttpHeaders.AUTHORIZATION));
        // 2. Healenium-Internal-Token header stripped
        assertNull(downstreamHeaders.getFirst("Healenium-Internal-Token"));
        // 3. Healenium-Tenant-Id overwritten with verified tenant_id
        assertEquals(expectedTenantId.toString(), downstreamHeaders.getFirst("Healenium-Tenant-Id"));
    }

    @Test
    void testAuthEnabledValidApiKeyMutatesHeadersAndCallsChain() {
        properties.getAuth().setEnabled(true);
        UUID expectedTenantId = UUID.randomUUID();
        when(iamAuthClient.validateApiKey("hlm_live_sdk_key")).thenReturn(Mono.just(expectedTenantId));
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.post("/healenium/elements")
                .header(TenantAuthGlobalFilter.API_KEY_HEADER, "hlm_live_sdk_key")
                .header("Healenium-Tenant-Id", "forged-tenant")
                .header("Healenium-Internal-Token", "forged-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerWebExchange downstreamExchange = captor.getValue();
        HttpHeaders downstreamHeaders = downstreamExchange.getRequest().getHeaders();

        // 1. X-Api-Key header stripped
        assertNull(downstreamHeaders.getFirst(TenantAuthGlobalFilter.API_KEY_HEADER));
        // 2. Healenium-Internal-Token header stripped
        assertNull(downstreamHeaders.getFirst("Healenium-Internal-Token"));
        // 3. Healenium-Tenant-Id set to verified tenant ID
        assertEquals(expectedTenantId.toString(), downstreamHeaders.getFirst("Healenium-Tenant-Id"));
    }

    @Test
    void testAuthEnabledInvalidApiKeyRejects401() {
        properties.getAuth().setEnabled(true);
        when(iamAuthClient.validateApiKey("hlm_live_bad")).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.post("/healenium/elements")
                .header(TenantAuthGlobalFilter.API_KEY_HEADER, "hlm_live_bad")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }
}
