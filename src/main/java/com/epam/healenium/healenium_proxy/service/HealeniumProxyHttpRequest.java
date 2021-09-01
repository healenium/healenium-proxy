package com.epam.healenium.healenium_proxy.service;

import org.apache.http.client.methods.HttpRequestBase;

public interface HealeniumProxyHttpRequest {
    String executeHttpRequest(HttpRequestBase httpRequest);
}
