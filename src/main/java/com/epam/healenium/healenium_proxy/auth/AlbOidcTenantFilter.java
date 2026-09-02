package com.epam.healenium.healenium_proxy.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Reads the trusted X-Amzn-Oidc-Data header injected by AWS ALB after JWT validation.
 * Extracts iss+sub, resolves tenant membership, and stores the result in exchange attributes
 * for downstream filters and controllers.
 *
 * Only active when healenium.auth.enabled=true.
 * Test-runner paths (/session/**, /wd/hub/**) are skipped — they use the API key flow.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "healenium.auth", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AlbOidcTenantFilter implements WebFilter, Ordered {

    public static final String RESOLVED_TENANT_ATTR = "healenium.resolved.tenant.id";
    public static final String RESOLVED_ISS_ATTR    = "healenium.resolved.iss";
    public static final String RESOLVED_SUB_ATTR    = "healenium.resolved.sub";
    public static final String RESOLVED_EMAIL_ATTR    = "healenium.resolved.email";
    public static final String RESOLVED_USERNAME_ATTR = "healenium.resolved.username";

    private static final String OIDC_DATA_HEADER = "X-Amzn-Oidc-Data";
    // Paths skipped entirely — test-runner WebDriver flows use API key auth
    private static final List<String> SKIP_PATTERNS = List.of(
            "/session", "/session/**", "/wd/hub/**",
            "/hlm-proxy/logout",
            "/hlm-proxy/actuator/health", "/hlm-proxy/actuator/health/**"
    );
    // Paths that only need iss/sub resolved — no tenant selection (bootstrap endpoints)
    private static final List<String> IDENTITY_ONLY_PATTERNS = List.of("/hlm-proxy/me");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final MembershipClient membershipClient;
    private final TenantResolver tenantResolver;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (SKIP_PATTERNS.stream().anyMatch(p -> PATH_MATCHER.match(p, path))) {
            return chain.filter(exchange);
        }

        String albData = exchange.getRequest().getHeaders().getFirst(OIDC_DATA_HEADER);
        if (!StringUtils.hasText(albData)) {
            log.warn("AlbOidcTenantFilter: missing {} on path {}", OIDC_DATA_HEADER, path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String[] parts = albData.split("\\.");
            if (parts.length < 2) {
                log.warn("AlbOidcTenantFilter: malformed OIDC data header on path {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            // Decode payload without verifying signature — ALB already validated the token
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JSONObject claims = new JSONObject(new String(payloadBytes));
            String iss = claims.optString("iss", null);
            String sub = claims.optString("sub", null);

            if (!StringUtils.hasText(iss) || !StringUtils.hasText(sub)) {
                log.warn("AlbOidcTenantFilter: OIDC payload missing iss/sub on path {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            log.debug("AlbOidcTenantFilter: OIDC claims keys = {}", claims.keySet());
            exchange.getAttributes().put(RESOLVED_ISS_ATTR, iss);
            exchange.getAttributes().put(RESOLVED_SUB_ATTR, sub);
            String email = claims.optString("email", null);
            if (StringUtils.hasText(email)) {
                exchange.getAttributes().put(RESOLVED_EMAIL_ATTR, email);
            }
            String username = claims.optString("username", null);
            if (StringUtils.hasText(username)) {
                exchange.getAttributes().put(RESOLVED_USERNAME_ATTR, username);
            }

            // Bootstrap endpoints only need identity — skip tenant selection
            if (IDENTITY_ONLY_PATTERNS.stream().anyMatch(p -> PATH_MATCHER.match(p, path))) {
                return chain.filter(exchange);
            }

            String requestedTenant = exchange.getRequest().getHeaders()
                    .getFirst(TenantAuthGlobalFilter.TENANT_HEADER);
            return membershipClient.resolveTenantIds(iss, sub)
                    .flatMap(tenants -> {
                        try {
                            UUID selected = tenantResolver.selectFromMembership(tenants, requestedTenant);
                            exchange.getAttributes().put(RESOLVED_TENANT_ATTR, selected.toString());
                            return chain.filter(exchange);
                        } catch (TenantResolver.TenantResolutionException e) {
                            log.warn("AlbOidcTenantFilter: tenant resolution failed: {}", e.getMessage());
                            exchange.getResponse().setStatusCode(e.getStatus());
                            return exchange.getResponse().setComplete();
                        }
                    });
        } catch (Exception e) {
            log.error("AlbOidcTenantFilter: failed to decode OIDC payload: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private static String padBase64(String base64) {
        return switch (base64.length() % 4) {
            case 2 -> base64 + "==";
            case 3 -> base64 + "=";
            default -> base64;
        };
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
