package com.epam.healenium.healenium_proxy.service;

import javax.servlet.http.HttpServletRequest;

public interface HealeniumHttpRequest {

    String getType();

    String execute(String uri, HttpServletRequest request);
}
