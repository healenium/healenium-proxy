package com.epam.healenium.healenium_proxy.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class HealeniumProxyHttpRequestImpl implements HealeniumProxyHttpRequest {
    /**
     * @param httpRequest
     * @return
     */
    @Override
    public String executeHttpRequest(HttpRequestBase httpRequest) {
        httpRequest.setHeader("Content-type", "application/json");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response;
        String responseData = "";

        try {
            response = client.execute(httpRequest);
            HttpEntity entityResponse = response.getEntity();
            responseData = EntityUtils.toString(entityResponse, StandardCharsets.UTF_8);
            client.close();
        } catch (IOException e) {
            log.error("Error during execute Http Request. Message: {}, Exception: {}", e.getMessage(), e);
        }
        return responseData;
    }
}
