package com.epam.healenium.healenium_proxy.rest;

import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.model.SessionDto;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j(topic = "healenium")
@Service
public class HealeniumRestService {

    private static final String HEALENIUM_REPORT_INIT_PATH = "/healenium/report/init/{uid}";
    private static final String HEALENIUM_SESSION_INIT_PATH = "/healenium/session";
    private static final String HEALENIUM_SAVE_ELEMENT_PATH = "/healenium/save/element";

    private double timeValue;

    @Value("${proxy.healenium.container.url}")
    private String healeniumContainerUrl;

    public void saveSessionId(String sessionId) {
        WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path(HEALENIUM_REPORT_INIT_PATH)
                        .build(sessionId))
                .retrieve()
                .bodyToMono(String.class)
                .subscribe();
    }

    public void restoreSessionOnServer(URL addressOfRemoteServer, String sessionId, Map<String, Object> sessionCapabilities) {
        SessionDto sessionDto = new SessionDto(addressOfRemoteServer, sessionId, sessionCapabilities);
        final long then = System.currentTimeMillis();
        WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri(HEALENIUM_SESSION_INIT_PATH)
                .bodyValue(sessionDto)
                .retrieve()
                .bodyToMono(Void.TYPE)
                .block();
        System.out.println("HEALENIUM_SESSION_INIT_PATH time: " + (System.currentTimeMillis() - then) / 1000.0);
    }

    public void saveElements(List<String> ids) {
        final long then = System.currentTimeMillis();
        WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri(HEALENIUM_SAVE_ELEMENT_PATH)
                .bodyValue(ids)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe();
        System.out.println("HEALENIUM_SAVE_ELEMENT_PATH time: " + (System.currentTimeMillis() - then) / 1000.0);
    }

    public String executeToSeleniumServer(HttpRequest httpRequest, SessionContext sessionContext) {
        final long then = System.currentTimeMillis();
        log.warn("Selenium server request: {}, {}", httpRequest.getMethod(), httpRequest.getUri());
        HttpResponse response = sessionContext.getHttpClient().execute(httpRequest);
                String result = new BufferedReader(
                new InputStreamReader(response.getContent().get(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
        log.warn("Selenium server response: {}", result);
        timeValue = timeValue + (System.currentTimeMillis() - then) / 1000.0;
        System.out.println("selenium timeValue: " + timeValue);
        return result;

    }

}
