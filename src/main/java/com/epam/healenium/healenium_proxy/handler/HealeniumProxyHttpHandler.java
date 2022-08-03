package com.epam.healenium.healenium_proxy.handler;

import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequestFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Slf4j
@Configuration("/**")
public class HealeniumProxyHttpHandler implements HttpRequestHandler {

    private final ProxyResponseConverter proxyResponseConverter;

    public HealeniumProxyHttpHandler(ProxyResponseConverter healeniumProxyUtils) {
        this.proxyResponseConverter = healeniumProxyUtils;
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter writer = response.getWriter();
        try {
            response.setContentType("application/json");

            HealeniumHttpRequest healeniumHttpRequest = HealeniumHttpRequestFactory.getRequest(request.getMethod());

            String data = healeniumHttpRequest.execute(request);
            writer.write(data);
        } catch (Exception e) {
            log.error("Error during handle Proxy Request. Message: {}, Exception: {}", e.getMessage(), e);
            response.setStatus(proxyResponseConverter.getHttpStatusCode(e));
            writer.write(proxyResponseConverter.generateResponse(e));
        } finally {
            writer.close();
        }
    }
}
