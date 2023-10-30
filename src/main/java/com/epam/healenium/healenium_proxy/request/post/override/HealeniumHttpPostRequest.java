package com.epam.healenium.healenium_proxy.request.post.override;


import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public interface HealeniumHttpPostRequest {

    String getURL();

    String execute(HttpServletRequest request) throws IOException;
}
