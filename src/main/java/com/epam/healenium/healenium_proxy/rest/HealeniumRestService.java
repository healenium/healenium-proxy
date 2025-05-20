package com.epam.healenium.healenium_proxy.rest;

import com.epam.healenium.healenium_proxy.model.BackendHealthCheckDto;
import com.epam.healenium.healenium_proxy.model.ReportContentDto;
import com.epam.healenium.healenium_proxy.model.ReportDto;
import com.epam.healenium.healenium_proxy.model.SeleniumHealthCheckDto;
import com.epam.healenium.healenium_proxy.model.SessionDto;
import com.epam.healenium.healenium_proxy.model.elitea.EliteaDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "healenium")
@Service
public class HealeniumRestService {

    private static final String HEALENIUM_SESSION_INIT_PATH = "/healenium/session";
    private static final String SELENIUM_HEALTH_CHECK_URI = "/status";
    private static final String BACKEND_HEALTH_CHECK_URI = "/actuator/health";
    private static final String GET_ALL_REPORTS_URI = "/healenium/report/reports/";
    private static final String UPDATE_LOCATOR_CODE_POSITION = "/healenium/elitea/v2/";

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
                .bodyToMono(new ParameterizedTypeReference<List<ReportDto>> () {});
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

    public Mono<EliteaDto> updateLocatorCodePosition(String reportId, String projectName, String repoName, String authorizationHeader) {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .build()
                .get()
                .uri(UPDATE_LOCATOR_CODE_POSITION + reportId + "/" + projectName + "/" + repoName)
                .retrieve()
                .bodyToMono(EliteaDto.class);
    }

    public Mono<EliteaDto> createMR(String reportId, String projectName, String repoName, String authorizationHeader) {
        return WebClient.builder()
                .baseUrl(healeniumContainerUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .build()
                .get()
                .uri(UPDATE_LOCATOR_CODE_POSITION + "mr/" + reportId + "/" + projectName + "/" + repoName)
                .retrieve()
                .bodyToMono(EliteaDto.class);
    }
}
