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
         * Anonymous tenant id. Requests with no tenant use this value.
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

