package com.epam.healenium.healenium_proxy.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class HealeniumRestService {

    @Value("${proxy.healenium.container.url}")
    private String healeniumContainerUrl;

    public void saveSessionId(String sessionId) {
        WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/healenium/report/init/{uid}")
                        .build(sessionId))
                .retrieve()
                .bodyToMono(String.class)
                .subscribe();
    }

}
