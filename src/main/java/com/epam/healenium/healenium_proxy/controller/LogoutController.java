package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.auth.HealeniumAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Clears ALB session cookies and redirects to Cognito logout endpoint.
 * Required because ALB session cookies survive Cognito token invalidation.
 */
@RestController
@RequestMapping("/hlm-proxy/logout")
@RequiredArgsConstructor
public class LogoutController {

    private static final String[] ALB_COOKIE_NAMES = {
            "AWSELBAuthSessionCookie-0",
            "AWSELBAuthSessionCookie-1"
    };

    private final HealeniumAuthProperties authProperties;

    @GetMapping
    public Mono<Void> logout(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();

        for (String cookieName : ALB_COOKIE_NAMES) {
            response.addCookie(ResponseCookie.from(cookieName, "")
                    .maxAge(Duration.ZERO)
                    .path("/")
                    .httpOnly(true)
                    .secure(true)
                    .build());
        }

        String cognitoDomain = authProperties.getAuth().getCognitoDomain();
        String clientId = authProperties.getAuth().getClientId();
        String logoutUri = resolveLogoutUri(exchange);

        String redirectUrl = cognitoDomain + "/logout?client_id=" + clientId
                + "&logout_uri=" + URLEncoder.encode(logoutUri, StandardCharsets.UTF_8);

        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(redirectUrl));
        return response.setComplete();
    }

    private String resolveLogoutUri(ServerWebExchange exchange) {
        String configured = authProperties.getAuth().getLogoutUri();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String proto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        String host = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Host");
        if (host == null) host = exchange.getRequest().getHeaders().getFirst("Host");
        if (proto == null) proto = "https";
        return proto + "://" + host + "/";
    }
}
