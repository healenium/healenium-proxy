package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.healenium_proxy.service.HealeniumHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Service
public class HealeniumGetRequest implements HealeniumHttpRequest {

    private final HealeniumBaseRequest healeniumBaseRequest;

    public HealeniumGetRequest(HealeniumBaseRequest healeniumBaseRequest) {
        this.healeniumBaseRequest = healeniumBaseRequest;
    }

    @Override
    public String getType() {
        return "GET";
    }

    @Override
    public String execute(String uri, HttpServletRequest request) {
        HttpRequestBase httpGet = new HttpGet(uri);
        return healeniumBaseRequest.executeBaseRequest(httpGet);
    }
}
