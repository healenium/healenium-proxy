package com.epam.healenium.healenium_proxy.controller;

import com.epam.healenium.healenium_proxy.model.elitea.*;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j(topic = "healenium")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api")
public class ApiController {

    private final HealeniumRestService restService;
    private final ObjectMapper objectMapper;

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

    @GetMapping("/elitea/selector-detection/{reportId}")
    public Mono<ResponseEntity<List<EliteaSelectorDetectionResponseDto>>> selectorDetectionByReport(@PathVariable String reportId) {
        return restService.selectorDetectionByReport(reportId)
                .flatMap(listSelectors -> {
                    try {
                        List<EliteaSelectorDetectionRequestDto> availableSelectors = listSelectors.stream()
                                .filter(s -> s != null && !CollectionUtils.isEmpty(s.getPathList()))
                                .toList();
                        String dtoAsJson = objectMapper.writeValueAsString(availableSelectors);
                        RunEliteaAgentRequest request = new RunEliteaAgentRequest(Collections.emptyList(), dtoAsJson);
                        String userInputJson = objectMapper.writeValueAsString(request);
                        log.info("[ELITEA] Selector Detection Request: " + userInputJson);
                        Mono<IntegrationFormDto> credentialsDto = restService.getCredentials();
                        return credentialsDto.flatMap(credentials -> {
                                    Mono<String> stringMono = restService.runEliteaAgent(userInputJson, "11", credentials.getEliteaToken());
                                    Mono<List<EliteaSelectorDetectionResponseDto>> eliteaSelectorDetectionResponseDtoMono = stringMono
                                            .flatMap(s -> parseChatHistoryContentList(s, EliteaSelectorDetectionResponseDto.class));
                                    Mono<List<EliteaSelectorDetectionResponseDto>> convertedList = eliteaSelectorDetectionResponseDtoMono
                                            .map(this::convert);
                                    List<EliteaSelectorDetectionResponseDto> list = listSelectors.stream()
                                            .filter(s -> s != null && CollectionUtils.isEmpty(s.getPathList()))
                                            .map(this::convert3)
                                            .toList();
                                    return Mono.zip(convertedList, Mono.just(list))
                                            .map(tuple -> {
                                                List<EliteaSelectorDetectionResponseDto> combinedList = new ArrayList<>();
                                                combinedList.addAll(tuple.getT1());
                                                combinedList.addAll(tuple.getT2());
                                                return combinedList;
                                            });
                                }
                        );
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Error serialisation DTO", e));
                    }
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());

    }

    public List<EliteaSelectorDetectionResponseDto> convert2(List<EliteaSelectorDetectionRequestDto> requestDtos) {
        List<EliteaSelectorDetectionResponseDto> responseDtos = new ArrayList<>();
        for (EliteaSelectorDetectionRequestDto requestDto : requestDtos) {
            responseDtos.add(convert3(requestDto));
        }
        return responseDtos;
    }

    public EliteaSelectorDetectionResponseDto convert3(EliteaSelectorDetectionRequestDto requestDto) {
        if (requestDto != null) {
            return new EliteaSelectorDetectionResponseDto()
                    .setId(requestDto.getId())
                    .setLocator(requestDto.getLocator())
                    .setLocatorType(requestDto.getLocatorType())
                    .setInvalidPaths(requestDto.getPathList() != null && !requestDto.getPathList().isEmpty()
                            ? (requestDto.getPathList().stream().map(pl -> new PathDetails().setPath(pl)).toList())
                            : Collections.emptyList());
        }
        return null;
    }

    @GetMapping("/elitea/report/create-mr/{reportId}")
    public Mono<ResponseEntity<ContentResponse>> createPullRequest(@PathVariable String reportId) {
        return restService.createPullRequest(reportId)
                .flatMap(eliteaDto -> {
                    try {
                        String dtoAsJson = objectMapper.writeValueAsString(eliteaDto);
                        RunEliteaAgentRequest request = new RunEliteaAgentRequest(Collections.emptyList(), dtoAsJson);
                        String userInputJson = objectMapper.writeValueAsString(request);
                        log.info("[ELITEA] Create PR Request: " + userInputJson);
                        Mono<IntegrationFormDto> credentialsDto = restService.getCredentials();
                        return credentialsDto.flatMap(credentials -> {
                            Mono<String> stringMono = restService.runEliteaAgent(userInputJson, "12", credentials.getEliteaToken());
                            return stringMono.flatMap(s -> parseChatHistoryContent(s, ContentResponse.class));
                        });
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Error serialisation DTO", e));
                    }
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private <T> Mono<List<T>> parseChatHistoryContentList(String resp, Class<T> contentClass) {
        try {
            log.info("[ELITEA] String Response: " + resp);
            EliteaPRResponseDto contentResponse = objectMapper.readValue(resp, EliteaPRResponseDto.class);
            String content = contentResponse.getChat_history().stream()
                    .filter(chat -> "assistant".equals(chat.getRole()))
                    .findFirst()
                    .map(ChatHistory::getContent)
                    .orElse("");
            log.info("[ELITEA] Elitea Response: {}", content);
            List<T> parsedObjects = objectMapper.readValue(content, objectMapper.getTypeFactory().constructCollectionType(List.class, contentClass));

            return Mono.just(parsedObjects);
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to parse chat history content: ", e));
        }
    }

    private <T> Mono<T> parseChatHistoryContent(String resp, Class<T> contentClass) {
        try {
            EliteaPRResponseDto contentResponse = objectMapper.readValue(resp, EliteaPRResponseDto.class);
            String content = contentResponse.getChat_history().stream()
                    .filter(chat -> "assistant".equals(chat.getRole()))
                    .findFirst()
                    .map(ChatHistory::getContent)
                    .orElse("");
            log.info("[ELITEA] Elitea Response: {}", content);
            T contentResponse1 = objectMapper.readValue(content, contentClass);
            return Mono.just(contentResponse1);
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to parse chat history content: ", e));
        }
    }

    private List<EliteaSelectorDetectionResponseDto> convert(List<EliteaSelectorDetectionResponseDto> sourceList) {
        List<EliteaSelectorDetectionResponseDto> resultList = new ArrayList<>();
        for (EliteaSelectorDetectionResponseDto source : sourceList) {
            EliteaSelectorDetectionResponseDto target = new EliteaSelectorDetectionResponseDto()
                    .setId(source.getId())
                    .setLocator(source.getLocator())
                    .setLocatorType(source.getLocatorType());
            if (source.getValidPaths().isEmpty()) {
                target.setInvalidPaths(source.getInvalidPaths());
            } else if (source.getValidPaths().size() > 1) {
                target.setInvalidPaths(source.getValidPaths());
            }
            resultList.add(target);
        }
        return resultList;
    }

}
