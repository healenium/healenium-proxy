package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.http.HttpRequest;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Slf4j(topic = "healenium")
@Service
public class HealeniumCreateSessionPostRequest implements HealeniumHttpPostRequest {

    private final HttpServletRequestService servletRequestService;
    private final HealeniumRestService healeniumRestService;
    private final SessionContextService sessionContextService;
    private final JsonMapper jsonMapper;

    public HealeniumCreateSessionPostRequest(HttpServletRequestService servletRequestService,
                                             HealeniumRestService healeniumRestService,
                                             SessionContextService sessionContextService,
                                             JsonMapper jsonMapper) {
        this.servletRequestService = servletRequestService;
        this.healeniumRestService = healeniumRestService;
        this.sessionContextService = sessionContextService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String getURL() {
        return "session";
    }

    @Override
    public String execute(HttpServletRequest request) {
        SessionContext sessionContext = sessionContextService.initSessionContext(request);
        HttpRequest httpRequest = servletRequestService.encodePostRequest(request, sessionContext);
        String responseData = healeniumRestService.executeToSeleniumServer(httpRequest, sessionContext);
        log.debug("[Create Session] Response from Selenium Server: {}", responseData);
        if (jsonMapper.isErrorResponse(responseData)) {
            return responseData;
        }
        String sessionId = sessionContextService.submitSessionContext(responseData, sessionContext);
        healeniumRestService.restoreSessionOnServer(sessionContext.getUrl(), sessionId, sessionContext.getCapabilities());
        return responseData;
    }

}
