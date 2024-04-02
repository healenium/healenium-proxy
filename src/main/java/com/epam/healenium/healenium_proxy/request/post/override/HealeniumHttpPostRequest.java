package com.epam.healenium.healenium_proxy.request.post.override;


import com.epam.healenium.healenium_proxy.model.OriginalResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public interface HealeniumHttpPostRequest {

    String getURL();

    OriginalResponse execute(HttpServletRequest request) throws IOException;
}
