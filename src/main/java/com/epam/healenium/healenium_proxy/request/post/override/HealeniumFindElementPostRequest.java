package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.RestoreDriverServiceFactory;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.appium.java_client.MobileBy;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.function.Function;

import static org.openqa.selenium.remote.CapabilityType.PLATFORM_NAME;

@Slf4j
@Service
public class HealeniumFindElementPostRequest implements HealeniumHttpPostRequest {

    @Value("${proxy.healenium.container.url}")
    private String healeniumContainerUrl;

    @Value("${proxy.imitate.container.url}")
    private String imitateContainerUrl;

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

    protected final HealeniumProxyUtils proxyUtils;
    protected final HealeniumBaseRequest healeniumBaseRequest;
    protected final HealeniumRestService healeniumRestService;

    public HealeniumFindElementPostRequest(HealeniumProxyUtils proxyUtils,
                                           HealeniumBaseRequest healeniumBaseRequest,
                                           HealeniumRestService healeniumRestService) {
        this.proxyUtils = proxyUtils;
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.healeniumRestService = healeniumRestService;
    }

    @Override
    public String getURL() {
        return "element";
    }

    @Override
    public String execute(HttpServletRequest request) throws MalformedURLException, JsonProcessingException {
        String requestBody = proxyUtils.getRequestBody(request);

        String currentSessionId = proxyUtils.getCurrentSessionId(request);
        try {
            SessionDelegate sessionDelegate = healeniumBaseRequest.getSessionDelegateCache().get(currentSessionId);
            String platformName = (String) sessionDelegate.getCapabilities().get(PLATFORM_NAME);
            WebDriver selfHealingDriver = RestoreDriverServiceFactory.getRestoreService(platformName)
                    .restoreDriver(currentSessionId, sessionDelegate, getConfig(currentSessionId));
            By by = getLocator(requestBody);
            return getHealingResponse(selfHealingDriver, by);
        } catch (ClassCastException | MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        return Strings.EMPTY;
    }

    private By getLocator(String requestBody) {
        JSONObject jsonObj = new JSONObject(requestBody);
        String using = jsonObj.get("using").toString();
        String value = jsonObj.get("value").toString();
        return BY_MAP_ELEMENT.get(using).apply(value);
    }

    protected String getHealingResponse(WebDriver selfHealingDriver, By by) {
        WebElement currentWebElement = selfHealingDriver.findElement(by);
        return proxyUtils.generateResponse(currentWebElement);
    }

    private Config getConfig(String currentSessionId) throws MalformedURLException {
        URL healeniumUrl = new URL(healeniumContainerUrl);
        URL imitateUrl = new URL(imitateContainerUrl);
        return ConfigFactory.systemEnvironment()
                .withValue("sessionKey", ConfigValueFactory.fromAnyRef(currentSessionId))
                .withValue("serverHost", ConfigValueFactory.fromAnyRef(healeniumUrl.getHost()))
                .withValue("serverPort", ConfigValueFactory.fromAnyRef(healeniumUrl.getPort()))
                .withValue("imitateHost", ConfigValueFactory.fromAnyRef(imitateUrl.getHost()))
                .withValue("imitatePort", ConfigValueFactory.fromAnyRef(imitateUrl.getPort()))
                .withValue("proxy", ConfigValueFactory.fromAnyRef(true));
    }
}
