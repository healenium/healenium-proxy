package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.healenium_proxy.service.HealeniumHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class HealeniumGetRequest implements HealeniumHttpRequest {

    @Override
    public String getType() {
        return "GET";
    }

    @Override
    public String execute(String uri, HttpServletRequest request) {
        HttpRequestBase httpGet = new HttpGet(uri);;
        return executeRequest(httpGet);
    }

    protected String executeRequest(HttpRequestBase httpGet) {
        httpGet.setHeader("Content-type", "application/json");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response;
        String responseData = "";

        try {
            response = client.execute(httpGet);
            HttpEntity entityResponse = response.getEntity();
            responseData = EntityUtils.toString(entityResponse, StandardCharsets.UTF_8);
            client.close();
        } catch (IOException e) {
            log.error("Error during execute Http Request. Message: {}, Exception: {}", e.getMessage(), e);
        }
        return responseData;
    }
}
