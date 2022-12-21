package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@Service
public class HealeniumFindElementPostRequest implements HealeniumHttpPostRequest {

    protected final ProxyResponseConverter proxyResponseConverter;
    protected final SessionContextService sessionContextService;
    protected final JsonMapper jsonMapper;

    @Override
    public String getURL() {
        return "element";
    }

    @Override
    public String execute(HttpServletRequest request) {
        Map<String, Object> requestBodyMap = jsonMapper.convertRequest(request);
        String id = (String) requestBodyMap.get("id");

        SessionContext sessionContext = sessionContextService.getSessionContext(request);
        SelfHealingHandler selfHealingDriver = sessionContextService.getSelfHealingDriver(id, sessionContext);

        By by = jsonMapper.getBy(requestBodyMap);
        return findElement(by, selfHealingDriver, sessionContext);
    }

    protected String findElement(By by, SelfHealingHandler selfHealingDriver, SessionContext sessionContext) {
        WebElement currentWebElement = selfHealingDriver.findElement(by);
//        System.out.println("finded element: " + by + " id: " + ((RemoteWebElement) currentWebElement).getId());
        sessionContext.getWebElements().put(((RemoteWebElement) currentWebElement).getId(), currentWebElement);
        return proxyResponseConverter.generateResponse(currentWebElement);
    }

}
