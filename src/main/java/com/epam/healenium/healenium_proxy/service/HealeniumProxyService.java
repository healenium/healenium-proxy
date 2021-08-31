package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.SelfHealingDriver;
import com.epam.healenium.healenium_proxy.command.HealeniumCommandExecutor;
import com.epam.healenium.healenium_proxy.constants.Constants;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.codehaus.plexus.util.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

@Slf4j
public class HealeniumProxyService {

    public static final Map<String, BiFunction<String, HttpServletRequest, String>> REQ_MAP =
            ImmutableMap.<String, BiFunction<String, HttpServletRequest, String>>builder()
                    .put("DELETE", (uri, request) -> executeHttpRequest(new HttpDelete(uri)))
                    .put("GET", (uri, request) -> executeHttpRequest(new HttpGet(uri)))
                    .put("POST", (uri, request) -> executePostRequest(new HttpPost(uri), request))
                    .build();

    public static final Map<String, Function<String, By>> BY_MAP_ELEMENT =
            ImmutableMap.<String, Function<String, By>>builder()
                    .put("xpath", By::xpath)
                    .put("link text", By::linkText)
                    .put("partial link text", By::partialLinkText)
                    .put("css selector", By::cssSelector)
                    .build();

    /**
     * Get the body of the Post request and restore the current remote webdriver and heal occurs, if necessary
     *
     * @param httpPost
     * @param request
     * @return
     */
    private static String executePostRequest(HttpPost httpPost, HttpServletRequest request) {
        String requestBody = HealeniumProxyUtils.getRequestBody(request);
        String uri = request.getRequestURI();
        String response = Strings.EMPTY;

        if (Pattern.matches(Constants.HEALING_REGEX, uri)) {
            SessionId currentSessionId = HealeniumProxyUtils.getCurrentSessionId(request);
            try {
                RemoteWebDriver restoredWebDriverFromSession = restoreWebDriverFromSession(
                        new URL(Constants.SELENIUM_EXECUTOR), currentSessionId);
                SelfHealingDriver selfHealingDriver = SelfHealingDriver.create(restoredWebDriverFromSession);
                By by = BY_MAP_ELEMENT.get(HealeniumProxyUtils.getUsingFromRequest(requestBody))
                        .apply(HealeniumProxyUtils.getValueFromRequest(requestBody));
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

        return executeHttpRequest(httpPost);
    }

    private static String getHealingResponse(String uri, SelfHealingDriver selfHealingDriver, By by) {
        if (uri.contains("elements")) {
            List<WebElement> currentWebElements = selfHealingDriver.findElements(by);
            return HealeniumProxyUtils.generateResponse(currentWebElements);
        } else {
            WebElement currentWebElement = selfHealingDriver.findElement(by);
            return HealeniumProxyUtils.generateResponse(currentWebElement);
        }
    }

    /**
     * Executing http request and return response data
     *
     * @param httpRequest
     * @return
     */
    private static String executeHttpRequest(HttpRequestBase httpRequest) {
        httpRequest.setHeader("Content-type", "application/json");
        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response;
        String responseData = "";

        try {
            response = client.execute(httpRequest);
            HttpEntity entityResponse = response.getEntity();
            responseData = EntityUtils.toString(entityResponse, StandardCharsets.UTF_8);
            client.close();
        } catch (IOException e) {
            log.error("Error during execute Http Request. Message: {}, Exception: {}", e.getMessage(), e);
        }
        return responseData;
    }

    /**
     * Restore current remote webdriver
     *
     * @param commandExecutor
     * @param sessionId
     * @return
     */
    public static RemoteWebDriver restoreWebDriverFromSession(URL commandExecutor, SessionId sessionId) {
        CommandExecutor executor = new HealeniumCommandExecutor(commandExecutor, sessionId);
        return new RemoteWebDriver(executor, new DesiredCapabilities());
    }
}