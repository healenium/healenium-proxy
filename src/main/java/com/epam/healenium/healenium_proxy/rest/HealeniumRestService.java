package com.epam.healenium.healenium_proxy.rest;

import com.epam.healenium.healenium_proxy.model.OriginalResponse;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j(topic = "healenium")
@Service
public class HealeniumRestService {

    private static final String HEALENIUM_SESSION_INIT_PATH = "/healenium/session";

    @Value("${proxy.healenium.container.url}")
    private String healeniumContainerUrl;

    public void restoreSessionOnServer(URL addressOfRemoteServer, String sessionId, Map<String, Object> sessionCapabilities) {
        SessionDto sessionDto = new SessionDto(addressOfRemoteServer, sessionId, sessionCapabilities);
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
    }

    public OriginalResponse executeToSeleniumServer(HttpRequest httpRequest, SessionContext sessionContext) {
        HttpResponse response = sessionContext.getHttpClient().execute(httpRequest);
        String result = new BufferedReader(
                new InputStreamReader(response.getContent().get(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
        OriginalResponse originalResponse = new OriginalResponse();
        originalResponse.setBody(result);
        originalResponse.setStatus(response.getStatus());
        return originalResponse;

    }

}
