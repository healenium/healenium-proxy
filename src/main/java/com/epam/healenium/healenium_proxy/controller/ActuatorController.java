package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.model.BackendHealthCheckDto;
import com.epam.healenium.healenium_proxy.model.SeleniumHealthCheckDto;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j(topic = "healenium")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class ActuatorController {

    private final HealthEndpoint healthEndpoint;
    private final HealeniumRestService restService;

    @GetMapping("/actuator/health")
    public Mono<ResponseEntity<Map<String, String>>> getHealth() {
        HealthComponent proxyHealthCheck = healthEndpoint.health();
        Mono<SeleniumHealthCheckDto> seleniumHealthCheck = restService.healthCheckSelenium();
        Mono<BackendHealthCheckDto> backendHealthCheck = restService.healthCheckBackend();

        return Mono.zip(seleniumHealthCheck, backendHealthCheck)
                .map(tuple -> {
                    SeleniumHealthCheckDto seleniumHC = tuple.getT1();
                    BackendHealthCheckDto backendHC = tuple.getT2();

                    Map<String, String> hc = new HashMap<>();
                    hc.put("healenium-proxy", proxyHealthCheck.getStatus().getCode());
                    hc.put("selenium-grid", seleniumHC.getValue().isReady() ? "UP" : "DOWN");
                    hc.put("backend-service", backendHC.getStatus());

                    return ResponseEntity.ok(hc);
                });
    }
}
