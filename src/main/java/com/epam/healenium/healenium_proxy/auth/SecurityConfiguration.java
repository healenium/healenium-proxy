package com.epam.healenium.healenium_proxy.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(HealeniumAuthProperties.class)
public class SecurityConfiguration {

    /**
     * Auth enabled (SaaS): JWT validation is delegated to AWS ALB.
     * AlbOidcTenantFilter handles tenant resolution from trusted X-Amzn-Oidc-Data headers.
     */
    @Bean
    @ConditionalOnProperty(prefix = "healenium.auth", name = "enabled", havingValue = "true")
    public SecurityWebFilterChain proSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }

    /**
     * Auth disabled (free / on-premise): no authentication required.
     */
    @Bean
    @ConditionalOnProperty(prefix = "healenium.auth", name = "enabled", havingValue = "false", matchIfMissing = true)
    public SecurityWebFilterChain freeSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }
}
