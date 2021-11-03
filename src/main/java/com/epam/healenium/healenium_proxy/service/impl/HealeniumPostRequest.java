package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.SelfHealingDriver;
import com.epam.healenium.healenium_proxy.command.HealeniumCommandExecutor;
import com.epam.healenium.healenium_proxy.constants.Constants;
import com.epam.healenium.healenium_proxy.service.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import com.epam.healenium.healenium_proxy.util.HealeniumRestUtils;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.logging.log4j.util.Strings;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

@Slf4j
@Service
public class HealeniumPostRequest implements HealeniumHttpRequest {

    public static final Map<String, Function<String, By>> BY_MAP_ELEMENT =
            ImmutableMap.<String, Function<String, By>>builder()
                    .put("xpath", By::xpath)
                    .put("link text", By::linkText)
                    .put("partial link text", By::partialLinkText)
                    .put("css selector", By::cssSelector)
                    .build();

    private final HealeniumProxyUtils proxyUtils;
    private final HealeniumBaseRequest healeniumBaseRequest;
    private final HealeniumRestUtils healeniumRestUtils;

    public HealeniumPostRequest(HealeniumProxyUtils proxyUtils,
                                HealeniumBaseRequest healeniumBaseRequest,
                                HealeniumRestUtils healeniumRestUtils) {
        this.proxyUtils = proxyUtils;
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.healeniumRestUtils = healeniumRestUtils;
    }

    @Override
    public String getType() {
        return "POST";
    }

    @Override
    public String execute(String uri, HttpServletRequest request) {
        HttpPost httpPost = new HttpPost(uri);
        String requestBody = proxyUtils.getRequestBody(request);
        String requestURI = request.getRequestURI();
        String response = Strings.EMPTY;

        if (Pattern.matches(Constants.HEALING_REGEX, requestURI)
                || Pattern.matches(Constants.HEALING_REGEX_WEB_EL, requestURI)) {
            SessionId currentSessionId = proxyUtils.getCurrentSessionId(request);
            try {
                RemoteWebDriver restoredWebDriverFromSession = restoreWebDriverFromSession(
                        healeniumRestUtils.getSeleniumUrl(), currentSessionId);
                Config config = ConfigFactory.systemEnvironment()
                        .withValue("sessionKey", ConfigValueFactory.fromAnyRef(currentSessionId.toString()));
                SelfHealingDriver selfHealingDriver = SelfHealingDriver.create(restoredWebDriverFromSession, config);
                By by = BY_MAP_ELEMENT.get(proxyUtils.getUsingFromRequest(requestBody))
                        .apply(proxyUtils.getValueFromRequest(requestBody));
                response = getHealingResponse(requestURI, selfHealingDriver, by);
            } catch (ClassCastException e) {
                log.error(e.getMessage(), e);
            }
            return response;
        }

        StringEntity entity = null;
        try {
            entity = new StringEntity(requestBody);
        } catch (UnsupportedEncodingException e) {
            log.error("Error during execute Post Request. Message: {}, Exception: {}", e.getMessage(), e);
        }
        httpPost.setEntity(entity);

        return healeniumBaseRequest.executeBaseRequest(httpPost);
    }

    private RemoteWebDriver restoreWebDriverFromSession(URL commandExecutor, SessionId sessionId) {
        CommandExecutor executor = new HealeniumCommandExecutor(commandExecutor, sessionId);
        return new RemoteWebDriver(executor, new DesiredCapabilities());
    }

    private String getHealingResponse(String uri, SelfHealingDriver selfHealingDriver, By by) {
        if (uri.contains("elements")) {
            List<WebElement> currentWebElements = selfHealingDriver.findElements(by);
            return proxyUtils.generateResponse(currentWebElements);
        } else {
            WebElement currentWebElement = selfHealingDriver.findElement(by);
            return proxyUtils.generateResponse(currentWebElement);
        }
    }
}
