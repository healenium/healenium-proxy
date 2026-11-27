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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pro-mode CI/CD gateway (spec 004, Requirement 5): SDK allow-list, API key only, everything else 404. */
@ExtendWith(MockitoExtension.class)
class TenantAuthGlobalFilterTest {

    private static final String KEY = "hlm_live_sdk_key";

    @Mock
    private IamAuthClient iamAuthClient;
    @Mock
    private GatewayFilterChain chain;

    private HealeniumAuthProperties properties;
    private TenantAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new HealeniumAuthProperties();
        properties.getAuth().setEnabled(true);
        filter = new TenantAuthGlobalFilter(iamAuthClient, properties);
    }

    @Test
    void webDriverPath_passesThroughWithoutACheck() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/wd/hub/status").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(iamAuthClient);
    }

    @Test
    void communityMode_passesEveryPathThroughUnchanged() {
        properties.getAuth().setEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/healenium-iam/users/me").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertEquals("/healenium-iam/users/me", forwarded.getValue().getRequest().getURI().getPath());
        verifyNoInteractions(iamAuthClient);
    }

    @Test
    void iamPaths_answer404_andNeverReachTheChain() {
        assertNotServed("/healenium-iam/users/me");
        assertNotServed("/healenium-iam/auth/validate");
        assertNotServed("/healenium-proxy/me");
        assertNotServed("/healenium-proxy/settings/defaults");
        assertNotServed("/actuator/health");
    }

    @Test
    void backendPathOutsideTheAllowList_answers404_evenWithAValidKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healenium/report/all").header("X-Api-Key", KEY).build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        verifyNoInteractions(iamAuthClient, chain);
    }

    @Test
    void logsAndActuatorOnTheBackend_answer404() {
        assertBackend404(MockServerHttpRequest.get("/healenium/logs/time-range"));
        assertBackend404(MockServerHttpRequest.get("/healenium/actuator/health"));
        assertBackend404(MockServerHttpRequest.get("/screenshots/abc/shot.png"));
    }

    @Test
    void sdkPath_withoutAKey_answers401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/healenium/elements").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(iamAuthClient, chain);
    }

    @Test
    void sdkPath_withOnlyABearerToken_answers401_becauseTheDashboardNoLongerUsesThisPort() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healenium/elements").header(HttpHeaders.AUTHORIZATION, "Bearer eyJ.x.y").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(iamAuthClient, chain);
    }

    @Test
    void sdkPath_withAnInvalidKey_answers401() {
        when(iamAuthClient.validateApiKeyDetails("hlm_live_bad")).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healenium/elements").header("X-Api-Key", "hlm_live_bad").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    void sdkPath_withAValidKey_forwardsWithTheTenant_andStripsCredentials() {
        UUID tenant = UUID.randomUUID();
        when(iamAuthClient.validateApiKeyDetails(KEY)).thenReturn(Mono.just(auth(tenant, null)));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healenium/elements")
                        .header("X-Api-Key", KEY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer should-not-pass")
                        .header(HttpHeaders.COOKIE, "hlm_idp_token=should-not-pass")
                        .header("Healenium-Tenant-Id", "forged")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        HttpHeaders headers = forwarded.getValue().getRequest().getHeaders();
        assertEquals(tenant.toString(), headers.getFirst(TenantAuthGlobalFilter.TENANT_HEADER));
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(headers.getFirst(HttpHeaders.COOKIE));
        assertNull(headers.getFirst("X-Api-Key"));
        assertEquals(1, headers.get(TenantAuthGlobalFilter.TENANT_HEADER).size());
    }

    @Test
    void aiSelectorPost_withAValidKey_isForwarded() {
        UUID tenant = UUID.randomUUID();
        when(iamAuthClient.validateApiKeyDetails(KEY)).thenReturn(Mono.just(auth(tenant, null)));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/healenium-ai/selectors/xpath").header("X-Api-Key", KEY).build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        assertFalse(exchange.getResponse().isCommitted());
    }

    @Test
    void sdkPath_withAValidKey_setsTheKeyIdFromTheCache_andStripsAClientCopy() {
        UUID tenant = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        when(iamAuthClient.validateApiKeyDetails(KEY)).thenReturn(Mono.just(auth(tenant, keyId)));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healenium/elements")
                        .header("X-Api-Key", KEY)
                        .header(TenantAuthGlobalFilter.KEY_ID_HEADER, UUID.randomUUID().toString())
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        HttpHeaders headers = forwarded.getValue().getRequest().getHeaders();
        assertEquals(keyId.toString(), headers.getFirst(TenantAuthGlobalFilter.KEY_ID_HEADER));
        assertEquals(1, headers.get(TenantAuthGlobalFilter.KEY_ID_HEADER).size());
    }

    @Test
    void sdkPath_withATenantOnlyResult_sendsNoKeyIdHeader_evenWhenTheClientSendsOne() {
        UUID tenant = UUID.randomUUID();
        when(iamAuthClient.validateApiKeyDetails(KEY)).thenReturn(Mono.just(auth(tenant, null)));
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/healenium/elements")
                        .header("X-Api-Key", KEY)
                        .header(TenantAuthGlobalFilter.KEY_ID_HEADER, "forged-key-id")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertNull(forwarded.getValue().getRequest().getHeaders().getFirst(TenantAuthGlobalFilter.KEY_ID_HEADER));
    }

    private static IamAuthClient.CachedAuthResult auth(UUID tenant, UUID keyId) {
        return new IamAuthClient.CachedAuthResult(tenant, keyId, "AUTOMATION", "ACTIVE", null);
    }

    private void assertNotServed(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode(), path);
    }

    private void assertBackend404(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request.header("X-Api-Key", KEY).build());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }
}
