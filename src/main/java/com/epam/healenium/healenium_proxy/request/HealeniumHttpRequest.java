package com.epam.healenium.healenium_proxy.request;

import java.io.IOException;

import com.epam.healenium.healenium_proxy.model.OriginalResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface HealeniumHttpRequest {

    String getType();

    OriginalResponse execute(HttpServletRequest request) throws IOException;
}
