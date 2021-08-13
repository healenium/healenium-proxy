package com.epam.healenium.HealeniumProxy.controller;

import com.epam.healenium.HealeniumProxy.service.HealeniumProxyService;
import com.epam.healenium.HealeniumProxy.util.HealeniumProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.HttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class HealeniumProxyHttpHandler implements HttpRequestHandler {

    @Autowired
    private HealeniumProxyUtils utils;

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
