package com.epam.healenium.healenium_proxy.request;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;

public interface HealeniumHttpRequest {

    String getType();

    String execute(HttpServletRequest request) throws MalformedURLException, JsonProcessingException;
}
