package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.SelfHealingDriver;
import com.epam.healenium.healenium_proxy.command.HealeniumCommandExecutor;
import com.epam.healenium.healenium_proxy.constants.Constants;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.util.Strings;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
@Slf4j
public class HealeniumProxyPostRequestImpl implements HealeniumProxyPostRequest {

    @Autowired
    private HealeniumProxyUtils proxyUtils;

    @Override
    public String executePostRequest(HttpPost httpPost,
                                     HttpServletRequest request,
                                     Map<String, Function<String, By>> BY_MAP_ELEMENT) {
        String requestBody = proxyUtils.getRequestBody(request);
        String uri = request.getRequestURI();
        String response = Strings.EMPTY;

        if (Pattern.matches(Constants.HEALING_REGEX, uri)) {
            SessionId currentSessionId = proxyUtils.getCurrentSessionId(request);
            try {
                RemoteWebDriver restoredWebDriverFromSession = restoreWebDriverFromSession(
                        new URL(Constants.SELENIUM_EXECUTOR), currentSessionId);
                SelfHealingDriver selfHealingDriver = SelfHealingDriver.create(restoredWebDriverFromSession);
                By by = BY_MAP_ELEMENT.get(proxyUtils.getUsingFromRequest(requestBody))
                        .apply(proxyUtils.getValueFromRequest(requestBody));
                response = getHealingResponse(uri, selfHealingDriver, by);
            } catch (MalformedURLException | ClassCastException e) {
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

        httpPost.setHeader("Content-type", "application/json");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse httpResponse;
        String responseData = "";

        try {
            httpResponse = client.execute(httpPost);
            HttpEntity entityResponse = httpResponse.getEntity();
            responseData = EntityUtils.toString(entityResponse, StandardCharsets.UTF_8);
            client.close();
        } catch (IOException e) {
            log.error("Error during execute Post Request. Message: {}, Exception: {}", e.getMessage(), e);
        }
        return responseData;
    }

    public static RemoteWebDriver restoreWebDriverFromSession(URL commandExecutor, SessionId sessionId) {
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