package com.epam.healenium.healenium_proxy.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ProxyConfig {

    @Autowired
    private final Environment env;

    private Config config;

    public ProxyConfig(Environment env) {
        this.env = env;
    }

    public Config getConfig(String currentSessionId) {
        return config.withValue("sessionKey", ConfigValueFactory.fromAnyRef(currentSessionId));
    }

    @PostConstruct
    private void initConfig() {
        config = ConfigFactory.empty()
                .withValue("hlm.server.url", ConfigValueFactory.fromAnyRef(env.getProperty("proxy.healenium.container.url")))
                .withValue("hlm.imitator.url", ConfigValueFactory.fromAnyRef(env.getProperty("proxy.imitate.container.url")))
                .withValue("heal-enabled", ConfigValueFactory.fromAnyRef(env.getProperty("healing.healenabled")))
                .withValue("recovery-tries", ConfigValueFactory.fromAnyRef(env.getProperty("healing.recoverytries")))
                .withValue("score-cap", ConfigValueFactory.fromAnyRef(env.getProperty("healing.scorecap")))
                .withValue("proxy", ConfigValueFactory.fromAnyRef(true));
    }
}
