package com.epam.healenium.healenium_proxy.request.post;

import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.request.post.override.HealeniumHttpPostRequest;
import com.epam.healenium.healenium_proxy.request.post.override.HealeniumHttpPostRequestFactory;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

@Slf4j
@Service
public class HealeniumPostRequest implements HealeniumHttpRequest {

    private final HttpServletRequestService servletRequestService;
    private final HealeniumBaseRequest healeniumBaseRequest;

    public HealeniumPostRequest(HttpServletRequestService servletRequestService, HealeniumBaseRequest healeniumBaseRequest) {
        this.servletRequestService = servletRequestService;
        this.healeniumBaseRequest = healeniumBaseRequest;
    }

    @Override
    public String getType() {
        return "POST";
    }

    @Override
    public String execute(HttpServletRequest request) throws IOException {
        String requestURI = request.getRequestURI();
        HealeniumHttpPostRequest postRequest = HealeniumHttpPostRequestFactory.getRequest(requestURI);
        if (postRequest != null) {
            return postRequest.execute(request);
        }

        StringEntity entity = null;
        try {
            String requestBody = servletRequestService.getRequestBody(request);
            entity = new StringEntity(requestBody);
        } catch (UnsupportedEncodingException e) {
            log.error("Error during execute Post Request. Message: {}, Exception: {}", e.getMessage(), e);
        }

        String currentSessionId = servletRequestService.getCurrentSessionId(request);
        String url = healeniumBaseRequest.getSessionDelegateCache().get(currentSessionId).getUrl();
        HttpPost httpPost = new HttpPost(new URL(url) + requestURI);
        httpPost.setEntity(entity);

        return healeniumBaseRequest.executeToSeleniumServer(httpPost);
    }

}
