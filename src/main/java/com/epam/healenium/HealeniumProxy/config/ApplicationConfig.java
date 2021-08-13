package com.epam.healenium.HealeniumProxy.config;

import com.epam.healenium.HealeniumProxy.controller.HealeniumProxyHttpHandler;
import com.epam.healenium.HealeniumProxy.util.HealeniumProxyUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.HttpRequestHandler;

@Configuration
public class ApplicationConfig {

    @Bean
    public HealeniumProxyUtils getHealeniumProxyUtils() {
        return new HealeniumProxyUtils();
    }

    @Bean(name = "/**")
    public HttpRequestHandler httpRequestHandler() {
        return new HealeniumProxyHttpHandler();
    }
}
