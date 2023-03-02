package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.internal.StringUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
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
    private static double timeValue;

    @Override
    public String getURL() {
        return "element";
    }

    @Override
    public String execute(HttpServletRequest request) {
        Map<String, Object> requestBodyMap = jsonMapper.convertRequest(request);
        String id = (String) requestBodyMap.get("id");

//        SessionContext sessionContext2 = sessionContextService.getSessionContext(request);
        String currentSessionId = servletRequestService.getCurrentSessionId(request);
        SessionContext sessionContext = sessionContextService.getSessionContextCache().get(currentSessionId);
        SelfHealingHandler selfHealingDriver = sessionContextService.getSelfHealingDriver(id, sessionContext);
//        final long then = System.currentTimeMillis();
//        String s = healeniumRestService.executeToSeleniumServer(encodeGetRequest(currentSessionId), sessionContext);
//        timeValue = timeValue + (System.currentTimeMillis() - then) / 1000.0;
//        System.out.println("Get current url: " + timeValue + " " + s);
        By by = jsonMapper.getBy(requestBodyMap);
        return findElement(by, selfHealingDriver, sessionContext);
    }

    protected String findElement(By by, SelfHealingHandler selfHealingDriver, SessionContext sessionContext) {
        WebElement currentWebElement = selfHealingDriver.findElement(by);
//        System.out.println("finded element: " + by + " id: " + ((RemoteWebElement) currentWebElement).getId());
        sessionContext.getWebElements().put(((RemoteWebElement) currentWebElement).getId(), currentWebElement);
        return proxyResponseConverter.generateResponse(currentWebElement);
    }

    public HttpRequest encodeGetRequest(String currentSessionId) {
        HttpRequest request = new HttpRequest(HttpMethod.GET, "/session/" + currentSessionId + "/url");
        request.setHeader("Cache-Control", "no-cache");
        return request;
    }

}
