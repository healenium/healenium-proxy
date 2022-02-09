package com.epam.healenium.healenium_proxy.request.post.override;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;

public interface HealeniumHttpPostRequest {

    String getURL();

    String execute(HttpServletRequest request) throws MalformedURLException, JsonProcessingException;
}
