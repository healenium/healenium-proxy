package com.epam.healenium.healenium_proxy.rest;

import com.epam.healenium.healenium_proxy.model.*;
import com.epam.healenium.healenium_proxy.model.elitea.EliteaDto;
import com.epam.healenium.healenium_proxy.model.elitea.EliteaSelectorDetectionRequestDto;
import com.epam.healenium.healenium_proxy.model.elitea.IntegrationFormDto;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "healenium")
@Service
public class HealeniumRestService {

    private static final String HEALENIUM_SESSION_INIT_PATH = "/healenium/session";
    private static final String SELENIUM_HEALTH_CHECK_URI = "/status";
    private static final String BACKEND_HEALTH_CHECK_URI = "/actuator/health";
    private static final String GET_ALL_REPORTS_URI = "/healenium/report/data/";
    private static final String SELECTOR_DETECTION_URI = "/healenium/settings/selector-detection/";
    private static final String CREATE_PULL_REQUEST_URI = "/healenium/settings/pull-request/";
    private static final String GET_CREDENTIALS_URI = "/healenium/settings/credentials/";
    private static final String ELITEA_URL = "https://nexus.elitea.ai";
    private static final String ELITEA_AGENT_RUN = "/api/v1/applications/predict/prompt_lib/743/";

    @Value("${proxy.healenium.container.url}")
    private String healeniumContainerUrl;

    @Value("${proxy.selenium.url}")
    private String seleniumUrl;

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

    public Mono<SeleniumHealthCheckDto> healthCheckSelenium() {
        return WebClient.builder()
                .baseUrl(seleniumUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(SELENIUM_HEALTH_CHECK_URI)
                .retrieve()
                .bodyToMono(SeleniumHealthCheckDto.class);
    }

    public Mono<BackendHealthCheckDto> healthCheckBackend() {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(BACKEND_HEALTH_CHECK_URI)
                .retrieve()
                .bodyToMono(BackendHealthCheckDto.class);
    }

    public Mono<List<ReportDto>> getReports() {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(GET_ALL_REPORTS_URI)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ReportDto>>() {
                });
    }

    public Mono<ReportContentDto> getReports(String id) {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(GET_ALL_REPORTS_URI + id)
                .retrieve()
                .bodyToMono(ReportContentDto.class);
    }

    public Mono<byte[]> getImage(String screenshotPath) {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(screenshotPath)
                .retrieve()
                .bodyToMono(byte[].class);

    }

    public Mono<List<EliteaSelectorDetectionRequestDto>> selectorDetectionByReport(String reportId) {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(SELECTOR_DETECTION_URI + reportId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<EliteaSelectorDetectionRequestDto>>() {
                });
    }

    public Mono<EliteaDto> createPullRequest(String reportId) {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(CREATE_PULL_REQUEST_URI + reportId)
                .retrieve()
                .bodyToMono(EliteaDto.class);
    }

    public Mono<String> runEliteaAgent(String userInputJson, String promptId, String authorizationHeader) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(2))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(2, TimeUnit.MINUTES))
                        .addHandlerLast(new WriteTimeoutHandler(2, TimeUnit.MINUTES)));


        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(ELITEA_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authorizationHeader)
                .build()
                .post()
                .uri(ELITEA_AGENT_RUN + promptId)
                .bodyValue(userInputJson)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMinutes(2));
    }

    public Mono<IntegrationFormDto> getCredentials() {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri(GET_CREDENTIALS_URI + "proxy")
                .retrieve()
                .bodyToMono(IntegrationFormDto.class);
    }
}
