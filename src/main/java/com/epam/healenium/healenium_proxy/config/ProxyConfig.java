package com.epam.healenium.healenium_proxy.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProxyConfig {

    @Autowired
    private final Environment env;

    private Config config;

    public ProxyConfig(Environment env) {
        this.env = env;
        initConfig();
    }

    public Config getConfig(String currentSessionId) {
        return config.withValue("sessionKey", ConfigValueFactory.fromAnyRef(currentSessionId));
    }

    private void initConfig() {
        config = ConfigFactory.empty()
                .withValue("hlm.server.url", ConfigValueFactory.fromAnyRef(env.getProperty("proxy.healenium.container.url")))
                .withValue("hlm.imitator.url", ConfigValueFactory.fromAnyRef(env.getProperty("proxy.imitate.container.url")))
                .withValue("hlm.ai.url", ConfigValueFactory.fromAnyRef(env.getProperty("proxy.ai.container.url")))
                .withValue("heal-enabled", ConfigValueFactory.fromAnyRef(env.getProperty("healing.healenabled")))
                .withValue("recovery-tries", ConfigValueFactory.fromAnyRef(env.getProperty("healing.recoverytries")))
                .withValue("score-cap", ConfigValueFactory.fromAnyRef(env.getProperty("healing.scorecap")))
                .withValue("selector-type", ConfigValueFactory.fromAnyRef(env.getProperty("healing.selectortype", "cssSelector")))
                .withValue("backlight-healing", ConfigValueFactory.fromAnyRef(true))
                .withValue("proxy", ConfigValueFactory.fromAnyRef(true));
    }
    
    /**
     * Update selector type configuration
     * 
     * @param selectorType new selector type ("cssSelector" or "xpath")
     */
    public void updateSelectorType(String selectorType) {
        if (selectorType != null && (selectorType.equals("cssSelector") || selectorType.equals("xpath"))) {
            config = config.withValue("selector-type", ConfigValueFactory.fromAnyRef(selectorType));
        }
    }
}
