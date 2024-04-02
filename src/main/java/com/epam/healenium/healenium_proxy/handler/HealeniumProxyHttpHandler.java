package com.epam.healenium.healenium_proxy.handler;

import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.model.OriginalResponse;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequestFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.HttpRequestHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Slf4j(topic = "healenium")
@Configuration("/**")
public class HealeniumProxyHttpHandler implements HttpRequestHandler {

    private final ProxyResponseConverter proxyResponseConverter;

    public HealeniumProxyHttpHandler(ProxyResponseConverter proxyResponseConverter) {
        this.proxyResponseConverter = proxyResponseConverter;
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.debug("[Request Handler] Selenium Request URI: {}", request.getRequestURI());
        PrintWriter writer = null;
        try {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(String.valueOf(StandardCharsets.UTF_8));
            HealeniumHttpRequest healeniumHttpRequest = HealeniumHttpRequestFactory.getRequest(request.getMethod());
            OriginalResponse originalResponse = healeniumHttpRequest.execute(request);
            String responseBody = originalResponse.getBody();
            response.setStatus(originalResponse.getStatus());
            if (responseBody != null) {
                writer = response.getWriter();
                writer.write(responseBody);
            }
        } catch (Exception e) {
            log.error("[Request Handler] Error during handle Proxy Request. Message: {}, Exception: {}", e.getMessage(), e.toString());
            response.setStatus(proxyResponseConverter.getHttpStatusCode(e));
            writer = response.getWriter();
            writer.write(proxyResponseConverter.generateResponse(e));
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
