package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.OriginalResponse;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j(topic = "healenium")
@AllArgsConstructor
@Service
public class HealeniumFindElementPostRequest implements HealeniumHttpPostRequest {

    protected final ProxyResponseConverter proxyResponseConverter;
    protected final SessionContextService sessionContextService;
    protected final JsonMapper jsonMapper;
    protected final HealeniumRestService healeniumRestService;
    protected final HttpServletRequestService servletRequestService;

    @Override
    public String getURL() {
        return "element";
    }

    @Override
    public OriginalResponse execute(HttpServletRequest request) {
        Map<String, Object> requestBodyMap = jsonMapper.convertRequest(request);
        String id = (String) requestBodyMap.get("id");
        String currentSessionId = servletRequestService.getCurrentSessionId(request);
        SessionContext sessionContext = sessionContextService.getSessionContext(currentSessionId);
        SelfHealingHandler selfHealingDriver = sessionContextService.getSelfHealingDriver(id, sessionContext);
        By by = jsonMapper.getBy(requestBodyMap);
        String elementResponse = findElement(by, selfHealingDriver, sessionContext);
        OriginalResponse originalResponse = new OriginalResponse();
        originalResponse.setBody(elementResponse);
        originalResponse.setStatus(200);
        log.info("[Find Element] {}", elementResponse);
        return originalResponse;
    }

    protected String findElement(By by, SelfHealingHandler selfHealingDriver, SessionContext sessionContext) {
        WebElement currentWebElement = selfHealingDriver.findElement(by);
        sessionContext.getWebElements().put(((RemoteWebElement) currentWebElement).getId(), currentWebElement);
        return proxyResponseConverter.generateResponse(currentWebElement);
    }

}
