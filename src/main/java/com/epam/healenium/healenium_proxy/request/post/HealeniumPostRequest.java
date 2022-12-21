package com.epam.healenium.healenium_proxy.request.post;

import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.request.post.override.HealeniumHttpPostRequest;
import com.epam.healenium.healenium_proxy.request.post.override.HealeniumHttpPostRequestFactory;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.http.HttpRequest;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@Service
public class HealeniumPostRequest implements HealeniumHttpRequest {

    private final HttpServletRequestService servletRequestService;
    private final HealeniumRestService healeniumRestService;
    private final HealeniumHttpPostRequestFactory httpPostRequestFactory;
    private final SessionContextService sessionContextService;

    public HealeniumPostRequest(HttpServletRequestService servletRequestService,
                                HealeniumRestService healeniumRestService,
                                HealeniumHttpPostRequestFactory httpPostRequestFactory,
                                SessionContextService sessionContextService) {
        this.servletRequestService = servletRequestService;
        this.healeniumRestService = healeniumRestService;
        this.httpPostRequestFactory = httpPostRequestFactory;
        this.sessionContextService = sessionContextService;
    }

    @Override
    public String getType() {
        return "POST";
    }

    @Override
    public String execute(HttpServletRequest request) throws IOException {
        String requestURI = request.getRequestURI();
        HealeniumHttpPostRequest postRequest = httpPostRequestFactory.getRequest(requestURI);
        if (postRequest != null) {
            return postRequest.execute(request);
        }
        SessionContext sessionContext = sessionContextService.getSessionContext(request);
        HttpRequest httpRequest = servletRequestService.encodePostRequest(request, sessionContext);
        return healeniumRestService.executeToSeleniumServer(httpRequest, sessionContext);
    }

}
