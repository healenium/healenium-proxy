package com.epam.healenium.healenium_proxy.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "healenium")
public class HealeniumAuthProperties {

    private final Auth auth = new Auth();
    private final Apikey apikey = new Apikey();

    @Getter
    @Setter
    public static class Auth {
        /**
         * When true, UI and SDK routes require IAM Bearer JWT or API key.
         * WebDriver paths stay permitAll.
         */
        private boolean enabled = false;
        /** Post-logout redirect URI */
        private String logoutUri = "";
        /**
         * Tenant used for every request when auth is disabled (Community mode). It is not a fallback
         * for unauthenticated requests: with auth enabled, a request without a valid credential is rejected.
         */
        private String defaultTenantId = "00000000-0000-0000-0000-000000000001";
    }

    @Getter
    @Setter
    public static class Apikey {
        private String cacheTtl = "PT10M";
        private long cacheMaxSize = 10000;
    }
}

