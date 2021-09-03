package com.epam.healenium.healenium_proxy.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class HealeniumBaseRequest {

    protected String executeBaseRequest(HttpRequestBase httpRequest) {
        httpRequest.setHeader("Content-type", "application/json; charset=utf-8");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response;
        String responseData = Strings.EMPTY;

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
