package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.RestoreDriverService;
import com.epam.healenium.healenium_proxy.service.RestoreDriverServiceFactory;
import com.google.common.collect.ImmutableMap;
import io.appium.java_client.AppiumBy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.openqa.selenium.json.Json.MAP_TYPE;

@Slf4j
@AllArgsConstructor
@Service
public class HealeniumFindElementPostRequest implements HealeniumHttpPostRequest {

    public static final Map<String, Function<String, By>> BY_MAP_ELEMENT =
            ImmutableMap.<String, Function<String, By>>builder()
                    .put("css selector", By::cssSelector)
                    .put("link text", By::linkText)
                    .put("partial link text", By::partialLinkText)
                    .put("xpath", By::xpath)
                    .put("class name", By::className)
                    .put("id", By::id)
                    .put("name", By::name)
                    .put("tag name", By::tagName)
                    .put("accessibility id", AppiumBy::accessibilityId)
                    .put("-custom", AppiumBy::custom)
                    .put("-image", AppiumBy::image)
                    .put("class", By::className)
                    .build();

    protected final HttpServletRequestService httpServletRequestService;
    protected final ProxyResponseConverter proxyResponseConverter;
    protected final HealeniumBaseRequest healeniumBaseRequest;
    protected final HealeniumRestService healeniumRestService;
    protected final ProxyConfig proxyConfig;
    protected final RestoreDriverServiceFactory restoreDriverServiceFactory;

    private final Json json = new Json();

    @Override
    public String getURL() {
        return "element";
    }

    @Override
    public String execute(HttpServletRequest request) throws MalformedURLException {
        String requestBody = httpServletRequestService.getRequestBody(request);
        Map<String, Object> requestBodyMap = json.toType(requestBody, MAP_TYPE);

        By by = BY_MAP_ELEMENT.get((String) requestBodyMap.get("using"))
                .apply((String) requestBodyMap.get("value"));
        String id = (String) requestBodyMap.get("id");

        String currentSessionId = httpServletRequestService.getCurrentSessionId(request);
        SessionDelegate sessionDelegate = healeniumBaseRequest.getSessionDelegateCache().get(currentSessionId);
        SelfHealingHandler selfHealingDriver = getSelfHealingDriver(id, currentSessionId, sessionDelegate);

        return findElement(by, selfHealingDriver, sessionDelegate);
    }

    protected String findElement(By by, SelfHealingHandler selfHealingDriver, SessionDelegate sessionDelegate) {
        WebElement currentWebElement = selfHealingDriver.findElement(by);
        sessionDelegate.getWebElements().put(((RemoteWebElement) currentWebElement).getId(), currentWebElement);
        return proxyResponseConverter.generateResponse(currentWebElement);
    }

    private SelfHealingHandler getSelfHealingDriver(String id, String currentSessionId, SessionDelegate sessionDelegate) throws MalformedURLException {
        WebElement el = Optional.ofNullable(id)
                .map(idv -> sessionDelegate.getWebElements().get(idv))
                .orElse(null);
        RestoreDriverService restoreService = restoreDriverServiceFactory.getRestoreService(sessionDelegate.getCapabilities());
        return restoreService.restoreSelfHealingHandler(currentSessionId, sessionDelegate, el, proxyConfig.getConfig(currentSessionId));
    }

}
