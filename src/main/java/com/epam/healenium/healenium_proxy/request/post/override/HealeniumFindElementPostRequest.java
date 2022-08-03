package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.handlers.proxy.SelfHealingProxyInvocationHandler;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.RestoreDriverServiceFactory;
import com.google.common.collect.ImmutableMap;
import io.appium.java_client.MobileBy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.function.Function;

import static org.openqa.selenium.json.Json.MAP_TYPE;
import static org.openqa.selenium.remote.CapabilityType.PLATFORM_NAME;

@Slf4j
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
                    .put("accessibility id", MobileBy::AccessibilityId)
                    .put("-custom", MobileBy::custom)
                    .put("-image", MobileBy::image)
                    .put("class", By::className)
                    .build();

    protected final HttpServletRequestService httpServletRequestService;
    protected final ProxyResponseConverter proxyResponseConverter;
    protected final HealeniumBaseRequest healeniumBaseRequest;
    protected final HealeniumRestService healeniumRestService;
    protected final ProxyConfig proxyConfig;

    private final Json json = new Json();

    public HealeniumFindElementPostRequest(HttpServletRequestService httpServletRequestService,
                                           ProxyResponseConverter proxyResponseConverter,
                                           HealeniumBaseRequest healeniumBaseRequest,
                                           HealeniumRestService healeniumRestService,
                                           ProxyConfig proxyConfig) {
        this.httpServletRequestService = httpServletRequestService;
        this.proxyResponseConverter = proxyResponseConverter;
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.healeniumRestService = healeniumRestService;
        this.proxyConfig = proxyConfig;
    }

    @Override
    public String getURL() {
        return "element";
    }

    @Override
    public String execute(HttpServletRequest request) throws MalformedURLException {
        String requestBody = httpServletRequestService.getRequestBody(request);
        Map<String, Object> requestBodyMap = json.toType(requestBody, MAP_TYPE);

        String currentSessionId = httpServletRequestService.getCurrentSessionId(request);
        SessionDelegate sessionDelegate = healeniumBaseRequest.getSessionDelegateCache().get(currentSessionId);
        String platformName = (String) sessionDelegate.getCapabilities().get(PLATFORM_NAME);
        WebDriver selfHealingDriver = RestoreDriverServiceFactory.getRestoreService(platformName)
                .restoreDriver(currentSessionId, sessionDelegate, proxyConfig.getConfig(currentSessionId));
        By by = BY_MAP_ELEMENT.get((String) requestBodyMap.get("using"))
                .apply((String) requestBodyMap.get("value"));
        String id = (String) requestBodyMap.get("id");
        return findElement(selfHealingDriver, by, id, sessionDelegate);
    }

    protected String findElement(WebDriver selfHealingDriver, By by, String id, SessionDelegate sessionDelegate) {
        WebElement currentWebElement;
        if (id != null) {
            WebElement el = sessionDelegate.getWebElements().get(id);
            WebElement wrapEl = ((SelfHealingProxyInvocationHandler) Proxy.getInvocationHandler(selfHealingDriver))
                    .wrapElement(el, selfHealingDriver.getClass().getClassLoader());
            currentWebElement = wrapEl.findElement(by);
        } else {
            currentWebElement = selfHealingDriver.findElement(by);
        }
        sessionDelegate.getWebElements().put(((RemoteWebElement) currentWebElement).getId(), currentWebElement);
        return proxyResponseConverter.generateResponse(currentWebElement);
    }

}
