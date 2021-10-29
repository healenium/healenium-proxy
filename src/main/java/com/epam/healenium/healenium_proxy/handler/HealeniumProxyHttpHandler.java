package com.epam.healenium.healenium_proxy.handler;

import com.epam.healenium.healenium_proxy.service.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.service.HealeniumHttpRequestFactory;
import com.epam.healenium.healenium_proxy.util.HealeniumRestUtils;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class HealeniumProxyHttpHandler implements HttpRequestHandler {

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();

        HealeniumHttpRequest healeniumHttpRequest = HealeniumHttpRequestFactory.getRequest(request.getMethod());

        String data = healeniumHttpRequest.execute(HealeniumRestUtils.getSeleniumUrl() + request.getRequestURI(), request);

        writer.write(data);
    }
}
