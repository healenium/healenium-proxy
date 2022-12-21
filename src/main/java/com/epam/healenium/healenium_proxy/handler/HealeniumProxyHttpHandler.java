package com.epam.healenium.healenium_proxy.handler;

import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequestFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration("/**")
public class HealeniumProxyHttpHandler implements HttpRequestHandler {

    private final ProxyResponseConverter proxyResponseConverter;

    public HealeniumProxyHttpHandler(ProxyResponseConverter proxyResponseConverter) {
        this.proxyResponseConverter = proxyResponseConverter;
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) {
        System.out.println(request.getRequestURI());
        final long then = System.currentTimeMillis();
        PrintWriter writer = null;
        try {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(String.valueOf(StandardCharsets.UTF_8));
            writer = response.getWriter();

            HealeniumHttpRequest healeniumHttpRequest = HealeniumHttpRequestFactory.getRequest(request.getMethod());

            String data = healeniumHttpRequest.execute(request);
//            System.out.println("response: " + data);
            writer.write(data);
//            System.out.println((System.currentTimeMillis() - then) / 1000.0);
        } catch (Exception e) {
            log.error("Error during handle Proxy Request. Message: {}, Exception: {}", e.getMessage(), e);
            response.setStatus(proxyResponseConverter.getHttpStatusCode(e));
            writer.write(proxyResponseConverter.generateResponse(e));
        } finally {
            writer.close();
        }
    }
}
