package com.epam.healenium.healenium_proxy.rest;

import com.epam.healenium.healenium_proxy.util.HealeniumRestUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.MalformedURLException;

@Slf4j
@Service
public class HealeniumRestService {

    public void saveSessionId(String sessionId) throws MalformedURLException {
        WebClient.builder()
                .baseUrl(HealeniumRestUtils.getHealeniumUrl().toString())
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
