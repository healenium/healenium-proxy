package com.epam.healenium.healenium_proxy.service;


import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.google.common.net.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.openqa.selenium.remote.http.ClientConfig;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

@Slf4j(topic = "healenium")
@Component
public class HttpServletRequestService {

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(10);

    /**
     * Get request body from request
     *
     * @param request
     * @return
     */
    public String getRequestBody(HttpServletRequest request) {
        String requestBody = Strings.EMPTY;
        try {
            requestBody = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return requestBody;
    }

    public String getCurrentSessionId(HttpServletRequest request) {
        String[] split = request.getRequestURI().split("/");
        return split.length > 1 && "session".equals(split[1])
                ? request.getRequestURI().split("/")[2]
                : null;
    }

    public HttpRequest encodePostRequest(HttpServletRequest httpServletRequest, SessionContext sessionContext) {
        String requestURI = httpServletRequest.getRequestURI();
        HttpRequest request = new HttpRequest(HttpMethod.POST, sessionContext.getUrl() + requestURI);
        String content = "/session".equals(requestURI)
                ? sessionContext.getCreateSessionReqBody()
                : getRequestBody(httpServletRequest);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        request.setHeader("Content-Length", String.valueOf(data.length));
        request.setHeader("Content-Type", MediaType.JSON_UTF_8.toString());
        request.setContent(Contents.bytes(data));
        return request;
    }

    public HttpRequest encodeGetRequest(HttpServletRequest r, SessionContext sessionContext) {
        HttpRequest request = new HttpRequest(HttpMethod.GET, sessionContext.getUrl() + r.getRequestURI());
        request.setHeader("Cache-Control", "no-cache");
        return request;
    }

    public HttpRequest encodeDeleteRequest(HttpServletRequest r, SessionContext sessionContext) {
        return new HttpRequest(HttpMethod.DELETE, sessionContext.getUrl() + r.getRequestURI());
    }

    public HttpClient getHttpClient(URL url) {
        ClientConfig clientConfig = ClientConfig.defaultConfig()
                .baseUrl(url)
                .readTimeout(DEFAULT_READ_TIMEOUT);
        return HttpClient.Factory.createDefault().createClient(clientConfig);
    }
}
