package com.epam.healenium.healenium_proxy.request;

import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class HealeniumBaseRequest {

    @Getter
    private PassiveExpiringMap<String, SessionDelegate> sessionDelegateCache = new PassiveExpiringMap<>(1, TimeUnit.DAYS);

    public String executeToSeleniumServer(HttpRequestBase httpRequest) throws IOException {
        httpRequest.setHeader("Content-type", "application/json; charset=utf-8");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = client.execute(httpRequest);
        HttpEntity entityResponse = response.getEntity();
        String responseData = EntityUtils.toString(entityResponse, StandardCharsets.UTF_8);
        client.close();
        return responseData;
    }
}
