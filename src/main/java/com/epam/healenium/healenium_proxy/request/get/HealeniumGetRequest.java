package com.epam.healenium.healenium_proxy.request.get;

import com.epam.healenium.healenium_proxy.model.OriginalResponse;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.http.HttpRequest;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j(topic = "healenium")
@Service
public class HealeniumGetRequest implements HealeniumHttpRequest {

    private final HealeniumRestService healeniumRestService;
    private final HttpServletRequestService servletRequestService;
    private final SessionContextService sessionContextService;

    public HealeniumGetRequest(HealeniumRestService healeniumRestService,
                               HttpServletRequestService healeniumProxyUtils,
                               SessionContextService sessionContextService) {
        this.healeniumRestService = healeniumRestService;
        this.servletRequestService = healeniumProxyUtils;
        this.sessionContextService = sessionContextService;
    }

    @Override
    public String getType() {
        return "GET";
    }

    @Override
    public OriginalResponse execute(HttpServletRequest request) throws IOException {
        SessionContext sessionContext = sessionContextService.getSessionContext(request);
        HttpRequest httpRequest = servletRequestService.encodeGetRequest(request, sessionContext);
        return healeniumRestService.executeToSeleniumServer(httpRequest, sessionContext);
    }
}
