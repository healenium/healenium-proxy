package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.healenium_proxy.service.HealeniumHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Service
public class HealeniumDeleteRequest extends HealeniumGetRequest implements HealeniumHttpRequest {

    @Override
    public String getType() {
        return "DELETE";
    }

    @Override
    public String execute(String uri, HttpServletRequest request) {
        HttpRequestBase httpDelete = new HttpDelete(uri);
        return executeRequest(httpDelete);
    }
}
