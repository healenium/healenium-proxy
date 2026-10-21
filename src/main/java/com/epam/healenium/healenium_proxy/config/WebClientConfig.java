package com.epam.healenium.healenium_proxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public org.springframework.cloud.gateway.config.HttpClientCustomizer gatewayHttpClientCustomizer() {
        return httpClient -> httpClient.httpResponseDecoder(spec ->
                spec.maxHeaderSize(65536).maxInitialLineLength(16384)
        );
    }
}
