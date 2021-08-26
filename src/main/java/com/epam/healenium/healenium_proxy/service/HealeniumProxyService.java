package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.SelfHealingDriver;
import com.epam.healenium.healenium_proxy.constants.Constants;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyWebDriverUtils;
import com.google.common.collect.ImmutableMap;
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
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
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

public class HealeniumProxyService {
    private static final Logger LOGGER = LogManager.getLogger(HealeniumProxyService.class);
    private static SelfHealingDriver selfHealingDriver;

    public static final Map<String, BiFunction<String, HttpServletRequest, String>> REQ_MAP =
            ImmutableMap.<String, BiFunction<String, HttpServletRequest, String>>builder()
                    .put("DELETE", (uri, request) -> executeHttpRequest(new HttpDelete(uri)))
                    .put("GET", (uri, request) -> executeHttpRequest(new HttpGet(uri)))
                    .put("POST", (uri, request) -> executePostRequest(new HttpPost(uri), request))
                    .build();

    public static final Map<String, Function<String, WebElement>> BY_MAP_ELEMENT =
            ImmutableMap.<String, Function<String, WebElement>>builder()
                    .put("xpath", (value) -> selfHealingDriver.findElement(By.xpath(value)))
                    .put("link text", (value) -> selfHealingDriver.findElement(By.linkText(value)))
                    .put("partial link text", (value) -> selfHealingDriver.findElement(By.partialLinkText(value)))
                    .put("css selector", (value) -> selfHealingDriver.findElement(By.cssSelector(value)))
                    .build();

    public static final Map<String, Function<String, List<WebElement>>> BY_MAP_ELEMENTS =
            ImmutableMap.<String, Function<String, List<WebElement>>>builder()
                    .put("xpath", (value) -> selfHealingDriver.findElements(By.xpath(value)))
                    .put("link text", (value) -> selfHealingDriver.findElements(By.linkText(value)))
                    .put("partial link text", (value) -> selfHealingDriver.findElements(By.partialLinkText(value)))
                    .put("css selector", (value) -> selfHealingDriver.findElements(By.cssSelector(value)))
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

        if (Pattern.matches(Constants.HEALING_REGEX, uri)) {
            SessionId currentSessionId = HealeniumProxyUtils.getCurrentSessionId(request);
            RemoteWebDriver restoredWebDriverFromSession;
            WebElement currentWebElement = null;
            List<WebElement> currentWebElements = null;
            String response = "";

            try {
                restoredWebDriverFromSession =
                        HealeniumProxyWebDriverUtils.restoreWebDriverFromSession(
                                new URL(Constants.SELENIUM_EXECUTOR),
                                currentSessionId
                        );
                selfHealingDriver = SelfHealingDriver.create(restoredWebDriverFromSession);
                if (uri.contains("elements")) {
                    currentWebElements =
                            BY_MAP_ELEMENTS
                                    .get(HealeniumProxyUtils.getUsingFromRequest(requestBody))
                                    .apply(HealeniumProxyUtils.getValueFromRequest(requestBody));
                    response = HealeniumProxyUtils.generateResponse(currentWebElements);
                } else {
                    currentWebElement =
                            BY_MAP_ELEMENT
                                    .get(HealeniumProxyUtils.getUsingFromRequest(requestBody))
                                    .apply(HealeniumProxyUtils.getValueFromRequest(requestBody));
                    response = HealeniumProxyUtils.generateResponse(currentWebElement);
                }
            } catch (MalformedURLException | ClassCastException e) {
                LOGGER.error(e.getMessage(), e);
            }
            return response;
        }

        StringEntity entity = null;
        try {
            entity = new StringEntity(requestBody);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error(e.getMessage(), e);
        }
        httpPost.setEntity(entity);

        return executeHttpRequest(httpPost);
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
            LOGGER.error(e.getMessage(), e);
        }
        return responseData;
    }
}