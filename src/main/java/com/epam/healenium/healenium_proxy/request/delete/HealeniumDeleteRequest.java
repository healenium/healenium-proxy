package com.epam.healenium.healenium_proxy.request.delete;

import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.http.HttpRequest;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j(topic = "healenium")
@Service
public class HealeniumDeleteRequest implements HealeniumHttpRequest {

    private final HealeniumRestService healeniumRestService;
    private final HttpServletRequestService servletRequestService;
    private final SessionContextService sessionContextService;


    public HealeniumDeleteRequest(HealeniumRestService healeniumRestService,
                                  HttpServletRequestService healeniumProxyUtils,
                                  SessionContextService sessionContextService) {
        this.healeniumRestService = healeniumRestService;
        this.servletRequestService = healeniumProxyUtils;
        this.sessionContextService = sessionContextService;
    }

    @Override
    public String getType() {
        return "DELETE";
    }

    @Override
    public String execute(HttpServletRequest request) throws IOException {
        String currentSessionId = servletRequestService.getCurrentSessionId(request);
        SessionContext sessionContext = sessionContextService.getSessionContext(currentSessionId);
        HttpRequest httpRequest = servletRequestService.encodeDeleteRequest(request, sessionContext);
        if (String.format("/session/%s", currentSessionId).equals(request.getRequestURI())) {
            sessionContextService.deleteSessionContextFromCache(currentSessionId);
            sessionContext.getSelfHealingHandlerBase().quit();
        }
        return healeniumRestService.executeToSeleniumServer(httpRequest, sessionContext);
    }
}
