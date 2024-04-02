package com.epam.healenium.healenium_proxy.request.post;

import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.OriginalResponse;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.request.post.override.HealeniumHttpPostRequest;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Slf4j(topic = "healenium")
@Service
public class WaitCommandRequest implements HealeniumHttpPostRequest {

    private final JsonMapper jsonMapper;
    private final HttpServletRequestService servletRequestService;
    private final SessionContextService sessionContextService;

    public WaitCommandRequest(JsonMapper jsonMapper, HttpServletRequestService servletRequestService, SessionContextService sessionContextService) {
        this.jsonMapper = jsonMapper;
        this.servletRequestService = servletRequestService;
        this.sessionContextService = sessionContextService;
    }

    @Override
    public String getURL() {
        return "healenium/params";
    }

    @Override
    public OriginalResponse execute(HttpServletRequest request) throws IOException {
        String sessionId = servletRequestService.getCurrentSessionId(request);
        Map<String, Object> requestBodyMap = jsonMapper.convertRequest(request);
        Boolean isWaitCommand = (Boolean) requestBodyMap.get("isWait");
        log.info("[Wait] Wait Command: {}", isWaitCommand);
        SessionContext sessionContext = sessionContextService.getSessionContext(sessionId);
        if (sessionContext != null) {
            sessionContext.getSelfHealingEngine().getSessionContext().setWaitCommand(isWaitCommand);
            return new OriginalResponse().setStatus(204);
        } else {
            return new OriginalResponse().setStatus(404);
        }
    }
}
