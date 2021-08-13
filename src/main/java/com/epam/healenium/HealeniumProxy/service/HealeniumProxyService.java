package com.epam.healenium.HealeniumProxy.service;

import com.epam.healenium.HealeniumProxy.util.HealeniumProxyUtils;
import com.epam.healenium.SelfHealingDriver;
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
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.remote.SessionId;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class HealeniumProxyService {
    private static final Logger LOGGER = LogManager.getLogger(HealeniumProxyService.class);
    private static final HealeniumProxyUtils utils = new HealeniumProxyUtils();

    public static final Map<String, BiFunction<String, HttpServletRequest, String>> REQ_MAP =
            ImmutableMap.<String, BiFunction<String, HttpServletRequest, String>>builder()
                    .put("DELETE", (uri, request) -> executeHttpRequest(new HttpDelete(uri)))
                    .put("GET", (uri, request) -> executeHttpRequest(new HttpGet(uri)))
                    .put("POST", (uri, request) -> executePostRequest(new HttpPost(uri), request))
                    .build();

    /**
     * Get the body of the Post request and restore the current remote webdriver and heal occurs, if necessary
     * @param httpPost
     * @param request
     * @return
     */
    private static String executePostRequest(HttpPost httpPost, HttpServletRequest request) {
        String requestBody = utils.getRequestBody(request);

        if (Pattern.matches(utils.getHealingRegex(), request.getRequestURI())) {
            String xpath = utils.getXpathFromRequest(requestBody);

            SessionId currentSessionId = utils.getCurrentSessionId(request);
            RemoteWebDriver restoredWebDriverFromSession;
            WebElement currentWebElement = null;

            try {
                restoredWebDriverFromSession =
                        utils.restoreWebDriverFromSession(new URL(utils.getSeleniumExecutor()), currentSessionId);
                SelfHealingDriver selfHealingDriver = SelfHealingDriver.create(restoredWebDriverFromSession);
                currentWebElement = selfHealingDriver.findElement(By.xpath(xpath));
            } catch (MalformedURLException | ClassCastException e) {
                LOGGER.error(e.getMessage(), e);
            }
            return String.format(
                    utils.getWebElementPattern(),
                    currentSessionId,
                    ((RemoteWebElement) currentWebElement).getId());
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