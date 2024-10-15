package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j(topic = "healenium")
@AllArgsConstructor
@Component
public class ProxyFilterService {

    private static final String CONTENT_TYPE_VALUE = "application/json; charset=utf-8";
    private static final String CACHE_CONTROL_VALUE = "no-cache";

    private final JsonMapper jsonMapper;
    private final ProxyResponseConverter proxyResponseConverter;

    public String getUriVariable(ServerWebExchange exchange, String attribute) {
        String sessionId = null;
        Map<String, String> variables = exchange.getAttribute(ServerWebExchangeUtils.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables != null) {
            sessionId = variables.get(attribute);
        }
        return sessionId;
    }

    public Map<String, Object> getRequestBodyMap(DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        String body = new String(bytes, StandardCharsets.UTF_8);

        log.info("Find Element Request: {}", body);

        return jsonMapper.convertRequest(body);
    }

    public By getBy(DataBuffer dataBuffer) {
        Map<String, Object> requestBodyMap = getRequestBodyMap(dataBuffer);
        return jsonMapper.getBy(requestBodyMap);
    }

    public Mono<Void> getResponse(ServerHttpResponse response, String elementResponse) {
        byte[] bytesOut = elementResponse.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytesOut);
        response.setStatusCode(HttpStatus.OK);
        addResponseHeaders(response);
        return response.writeWith(Mono.just(buffer));
    }

    public Mono<Void> getResponse(ServerHttpResponse response, Exception e) {
        String jsonNoSuchElementException = proxyResponseConverter.generateResponse(e);
        byte[] bytesOut = jsonNoSuchElementException.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytesOut);
        response.setStatusCode(HttpStatus.NOT_FOUND);
        addResponseHeaders(response);
        return response.writeWith(Mono.just(buffer));
    }

    public void addResponseHeaders(ServerHttpResponse response) {
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_VALUE);
        headers.add(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }

}
