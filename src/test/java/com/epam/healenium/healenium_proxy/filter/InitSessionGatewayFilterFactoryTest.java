package com.epam.healenium.healenium_proxy.filter;

import com.epam.healenium.healenium_proxy.auth.HealeniumAuthProperties;
import com.epam.healenium.healenium_proxy.auth.IamAuthClient;
import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitSessionGatewayFilterFactoryTest {

    @Mock
    private ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory;
    @Mock
    private SessionContextService sessionContextService;
    @Mock
    private IamAuthClient iamAuthClient;
    @Mock
    private GatewayFilterChain chain;

    private HealeniumAuthProperties properties;
    private InitSessionGatewayFilterFactory filterFactory;

    @BeforeEach
    void setUp() {
        properties = new HealeniumAuthProperties();
        filterFactory = new InitSessionGatewayFilterFactory(
                modifyResponseBodyFilterFactory,
                sessionContextService,
                properties,
                iamAuthClient
        );
    }

    @Test
    void testExtractApiKeyFromAlwaysMatch() {
        String body = """
                {
                    "capabilities": {
                        "alwaysMatch": {
                            "browserName": "chrome",
                            "hlm:api_key": "hlm_live_always_123"
                        }
                    }
                }
                """;
        String extracted = InitSessionGatewayFilterFactory.extractApiKeyFromRequestBody(body);
        assertEquals("hlm_live_always_123", extracted);
    }

    @Test
    void testExtractApiKeyFromFirstMatch() {
        String body = """
                {
                    "capabilities": {
                        "firstMatch": [
                            {
                                "browserName": "firefox",
                                "hlm:api_key": "hlm_live_first_456"
                            }
                        ]
                    }
                }
                """;
        String extracted = InitSessionGatewayFilterFactory.extractApiKeyFromRequestBody(body);
        assertEquals("hlm_live_first_456", extracted);
    }

    @Test
    void testExtractApiKeyFromDesiredCapabilities() {
        String body = """
                {
                    "desiredCapabilities": {
                        "browserName": "edge",
                        "hlm:api_key": "hlm_live_desired_789"
                    }
                }
                """;
        String extracted = InitSessionGatewayFilterFactory.extractApiKeyFromRequestBody(body);
        assertEquals("hlm_live_desired_789", extracted);
    }

    @Test
    void testStripApiKeyFromRequestBody() {
        String body = """
                {
                    "capabilities": {
                        "alwaysMatch": {
                            "browserName": "chrome",
                            "hlm:api_key": "hlm_live_secret"
                        }
                    }
                }
                """;
        String stripped = InitSessionGatewayFilterFactory.stripApiKeyFromRequestBody(body);
        assertFalse(stripped.contains("hlm_live_secret"));
        assertFalse(stripped.contains("hlm:api_key"));
        assertTrue(stripped.contains("browserName"));
    }

    @Test
    void testAuthDisabledAllowsSessionWithoutKey() {
        properties.getAuth().setEnabled(false);

        GatewayFilter mockRewrite = mock(GatewayFilter.class);
        when(mockRewrite.filter(any(), any())).thenReturn(Mono.empty());
        when(modifyResponseBodyFilterFactory.apply(any(ModifyResponseBodyGatewayFilterFactory.Config.class)))
                .thenReturn(mockRewrite);

        String body = "{\"capabilities\":{\"alwaysMatch\":{\"browserName\":\"chrome\"}}}";
        MockServerHttpRequest request = MockServerHttpRequest.post("/session")
                .body(body);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new InitSessionGatewayFilterFactory.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(mockRewrite).filter(any(), eq(chain));
    }

    @Test
    void testAuthEnabledRejectsWhenKeyMissing() {
        properties.getAuth().setEnabled(true);

        String body = "{\"capabilities\":{\"alwaysMatch\":{\"browserName\":\"chrome\"}}}";
        MockServerHttpRequest request = MockServerHttpRequest.post("/session")
                .body(body);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new InitSessionGatewayFilterFactory.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void testAuthEnabledRejectsWhenKeyInvalidInIam() {
        properties.getAuth().setEnabled(true);
        when(iamAuthClient.validateApiKey("hlm_live_invalid")).thenReturn(Mono.empty());

        String body = """
                {
                    "capabilities": {
                        "alwaysMatch": {
                            "hlm:api_key": "hlm_live_invalid"
                        }
                    }
                }
                """;
        MockServerHttpRequest request = MockServerHttpRequest.post("/session")
                .body(body);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new InitSessionGatewayFilterFactory.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void testAuthEnabledPassesWhenKeyValidInIam() {
        properties.getAuth().setEnabled(true);
        UUID expectedTenant = UUID.randomUUID();
        when(iamAuthClient.validateApiKey("hlm_live_valid")).thenReturn(Mono.just(expectedTenant));

        GatewayFilter mockRewrite = mock(GatewayFilter.class);
        when(mockRewrite.filter(any(), any())).thenReturn(Mono.empty());
        when(modifyResponseBodyFilterFactory.apply(any(ModifyResponseBodyGatewayFilterFactory.Config.class)))
                .thenReturn(mockRewrite);

        String body = """
                {
                    "capabilities": {
                        "alwaysMatch": {
                            "hlm:api_key": "hlm_live_valid"
                        }
                    }
                }
                """;
        MockServerHttpRequest request = MockServerHttpRequest.post("/session")
                .body(body);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new InitSessionGatewayFilterFactory.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(iamAuthClient).validateApiKey("hlm_live_valid");
        verify(mockRewrite).filter(any(), eq(chain));
    }
}
