package com.epam.healenium.healenium_proxy.util;

import com.epam.healenium.healenium_proxy.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.remote.SessionId;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HealeniumProxyUtils {
    /**
     * Get request body from request
     *
     * @param request
     * @return
     */
    public String getRequestBody(HttpServletRequest request) {
        String requestBody = "";
        try {
            requestBody = new BufferedReader(new InputStreamReader(request.getInputStream()))
                    .lines().collect(Collectors.joining(""));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return requestBody;
    }

    public String getValueFromRequest(String requestBody) {
        JSONObject jsonObj = new JSONObject(requestBody);
        return jsonObj.get("value").toString();
    }

    public String getUsingFromRequest(String requestBody) {
        JSONObject jsonObj = new JSONObject(requestBody);
        return jsonObj.get("using").toString();
    }

    public SessionId getCurrentSessionId(HttpServletRequest request) {
        return new SessionId(request.getRequestURI().split("/")[2]);
    }

    public String generateResponse(List<WebElement> currentWebElements) {
        List<String> list = new ArrayList<>();
        currentWebElements.forEach(
                el -> list.add(String.format(Constants.PATTERN_WEB_ELEMENTS, ((RemoteWebElement) el).getId()))
        );
        return String.format(Constants.PATTERN_WEB_ELEMENTS_VALUES, list);
    }

    public String generateResponse(WebElement currentWebElement) {
        return String.format(
                Constants.PATTERN_WEB_ELEMENT,
                ((RemoteWebElement) currentWebElement).getId()
        );
    }
}
