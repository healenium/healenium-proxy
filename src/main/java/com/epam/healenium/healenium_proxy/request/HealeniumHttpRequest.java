package com.epam.healenium.healenium_proxy.request;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;

public interface HealeniumHttpRequest {

    String getType();

    String execute(HttpServletRequest request) throws IOException;
}
