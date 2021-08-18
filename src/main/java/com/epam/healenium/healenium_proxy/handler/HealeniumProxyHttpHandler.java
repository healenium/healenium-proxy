package com.epam.healenium.healenium_proxy.handler;

import com.epam.healenium.healenium_proxy.service.HealeniumProxyService;
import com.epam.healenium.healenium_proxy.util.impl.HealeniumProxyUtilsImpl;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class HealeniumProxyHttpHandler implements HttpRequestHandler {
    private static final HealeniumProxyUtilsImpl utils = new HealeniumProxyUtilsImpl();

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();

        String data = HealeniumProxyService.REQ_MAP
                .get(request.getMethod())
                .apply(utils.getSeleniumExecutor() + request.getRequestURI(), request);

        writer.write(data);
    }
}
