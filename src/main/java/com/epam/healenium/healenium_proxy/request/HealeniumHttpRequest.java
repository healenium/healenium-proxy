package com.epam.healenium.healenium_proxy.request;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public interface HealeniumHttpRequest {

    String getType();

    String execute(HttpServletRequest request) throws IOException;
}
