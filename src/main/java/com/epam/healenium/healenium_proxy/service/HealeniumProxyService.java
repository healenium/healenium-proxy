package com.epam.healenium.healenium_proxy.service;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.openqa.selenium.By;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

@Service
@Slf4j
public class HealeniumProxyService {

    private static HealeniumProxyHttpRequestImpl httpRequest;
    private static HealeniumProxyPostRequestImpl postRequest;

    @Autowired
    private HealeniumProxyService(
            HealeniumProxyHttpRequestImpl httpRequestImpl,
            HealeniumProxyPostRequestImpl postRequestImpl) {
        httpRequest = httpRequestImpl;
        postRequest = postRequestImpl;
    }

    public static final Map<String, Function<String, By>> BY_MAP_ELEMENT =
            ImmutableMap.<String, Function<String, By>>builder()
                    .put("xpath", By::xpath)
                    .put("link text", By::linkText)
                    .put("partial link text", By::partialLinkText)
                    .put("css selector", By::cssSelector)
                    .build();

    public static final Map<String, BiFunction<String, HttpServletRequest, String>> REQ_MAP =
            ImmutableMap.<String, BiFunction<String, HttpServletRequest, String>>builder()
                    .put("DELETE", (uri, request) -> httpRequest.executeHttpRequest(new HttpDelete(uri)))
                    .put("GET", (uri, request) -> httpRequest.executeHttpRequest(new HttpGet(uri)))
                    .put("POST", (uri, request) -> postRequest.executePostRequest(new HttpPost(uri), request, BY_MAP_ELEMENT))
                    .build();
}