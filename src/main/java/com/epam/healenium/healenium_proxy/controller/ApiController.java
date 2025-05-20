package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j(topic = "healenium")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api")
public class ApiController {

    private final HealeniumRestService restService;

    @GetMapping("/reports/{id}")
    public Mono<ResponseEntity<?>> getReports(@PathVariable String id) {
        return restService.getReports(id)
                .map(report -> {
                    if (report == null) {
                        return ResponseEntity.notFound().build();
                    }
                    report.getData().forEach(r -> r.setScreenShotPath("http://localhost:8085" + r.getScreenShotPath()));
                    return ResponseEntity.ok(report);
                });
    }

    @PostMapping("/elitea/locators/code-position/{reportId}")
    public Mono<ResponseEntity<?>> updateLocatorCodePosition(@RequestHeader("Authorization") String authorizationHeader,
                                                             @PathVariable String reportId) {
        String projectName = "healenium";
        String repositoryName = "healenium-example-maven";
        return restService.updateLocatorCodePosition(reportId, projectName, repositoryName, authorizationHeader)
                .map(report -> {
                    if (report == null) {
                        return ResponseEntity.notFound().build();
                    }
                    return ResponseEntity.ok(report);
                });
    }

    @GetMapping("/elitea/report/create-mr/{reportId}")
    public Mono<ResponseEntity<?>> createMR(@RequestHeader("Authorization") String authorizationHeader,
                                                             @PathVariable String reportId) {
        String projectName = "healenium";
        String repositoryName = "healenium-example-maven";
        return restService.createMR(reportId, projectName, repositoryName, authorizationHeader)
                .map(report -> {
                    if (report == null) {
                        return ResponseEntity.notFound().build();
                    }
                    return ResponseEntity.ok(report);
                });
    }


}
