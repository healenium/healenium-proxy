package com.epam.healenium.healenium_proxy.config;

import com.epam.healenium.healenium_proxy.handler.HealeniumProxyHttpHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.HttpRequestHandler;

@Configuration
public class HealeniumProxyAppConfig {

    @Bean(name = "/**")
    public HttpRequestHandler httpRequestHandler() {
        return new HealeniumProxyHttpHandler();
    }
}
