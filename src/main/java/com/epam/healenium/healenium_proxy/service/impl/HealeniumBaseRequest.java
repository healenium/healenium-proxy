package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.util.HealeniumRestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class HealeniumBaseRequest {
    private static String newSessionPath;

    private final HealeniumRestService healeniumRestService;

    public HealeniumBaseRequest(HealeniumRestService healeniumRestService,
                                @Value("${proxy.new.session.path}") String newSessionPath) {
        this.healeniumRestService = healeniumRestService;
        HealeniumBaseRequest.newSessionPath = newSessionPath;
    }

    protected String executeBaseRequest(HttpRequestBase httpRequest) {
        httpRequest.setHeader("Content-type", "application/json; charset=utf-8");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response;
        String responseData = Strings.EMPTY;

        try {
            response = client.execute(httpRequest);
            HttpEntity entityResponse = response.getEntity();
            responseData = EntityUtils.toString(entityResponse, StandardCharsets.UTF_8);
            if (newSessionPath.equals(httpRequest.getURI().getPath())) {
                Map<String, Object> result = new ObjectMapper().readValue(responseData, HashMap.class);
                Map<String, Object> value = (Map) result.get("value");
                String sessionId = (String) value.get("sessionId");
                healeniumRestService.saveSessionId(sessionId);
                URL reportInitUrl = HealeniumRestUtils.getReportInitUrl(sessionId);
                log.info("Report available at " + reportInitUrl);
            }
            client.close();
        } catch (IOException e) {
            log.error("Error during execute Http Request. Message: {}, Exception: {}", e.getMessage(), e);
        }
        return responseData;
    }
}
