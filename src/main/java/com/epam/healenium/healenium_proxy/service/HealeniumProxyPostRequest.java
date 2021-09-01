package com.epam.healenium.healenium_proxy.service;

import org.apache.http.client.methods.HttpPost;
import org.openqa.selenium.By;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Function;

public interface HealeniumProxyPostRequest {
    String executePostRequest(HttpPost httpPost,
                              HttpServletRequest request,
                              Map<String, Function<String, By>> BY_MAP_ELEMENT);
}
