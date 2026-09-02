package com.epam.healenium.healenium_proxy.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "healenium")
public class HealeniumAuthProperties {

    private final Auth auth = new Auth();
    private final M2m m2m = new M2m();
    private final Membership membership = new Membership();

    @Getter
    @Setter
    public static class Auth {
        /**
         * When true, UI routes to backend require a Cognito JWT + membership.
         * WebDriver paths stay permitAll.
         */
        private boolean enabled = false;
        /** Cognito hosted UI domain, e.g. https://healenium.auth.us-east-1.amazoncognito.com */
        private String cognitoDomain = "";
        private String clientId = "";
        /** Post-logout redirect URI (must be registered in Cognito App Client Allowed sign-out URLs) */
        private String logoutUri = "";
        /**
         * Fallback tenant UUID for single-tenant deployments where membership DB is not populated.
         * When non-empty, used instead of throwing FORBIDDEN on empty membership list.
         */
        private String defaultTenantId = "";
    }

    @Getter
    @Setter
    public static class M2m {
        private String internalToken = "";
    }

    @Getter
    @Setter
    public static class Membership {
        private String cacheTtl = "PT5M";
        private long cacheMaxSize = 10000;
    }
}
