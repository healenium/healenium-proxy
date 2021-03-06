package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.openqa.selenium.Platform;
import org.openqa.selenium.json.Json;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import static org.openqa.selenium.remote.CapabilityType.PLATFORM_NAME;

@Slf4j
@Service
public class HealeniumCreateSessionPostRequest implements HealeniumHttpPostRequest {

    @Value("${proxy.selenium.url}")
    private String seleniumUrl;

    @Value("${proxy.appium.url}")
    private String appiumUrl;

    @Value("${proxy.healenium.report.url}")
    private String healeniumReportUrl;

    private final HttpServletRequestService servletRequestService;
    private final HealeniumBaseRequest healeniumBaseRequest;
    private final HealeniumRestService healeniumRestService;

    private final Json json = new Json();

    public HealeniumCreateSessionPostRequest(HttpServletRequestService servletRequestService,
                                             HealeniumBaseRequest healeniumBaseRequest,
                                             HealeniumRestService healeniumRestService) {
        this.servletRequestService = servletRequestService;
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.healeniumRestService = healeniumRestService;
    }

    @Override
    public String getURL() {
        return "session";
    }

    @Override
    public String execute(HttpServletRequest request) throws IOException {
        String requestBody = servletRequestService.getRequestBody(request);
        Map<String, Map<String, Object>> bodyMap = json.toType(requestBody, Json.MAP_TYPE);
        String url = isWeb(bodyMap.get("desiredCapabilities")) ? seleniumUrl : appiumUrl;

        StringEntity entity = null;
        try {
            entity = new StringEntity(requestBody);
        } catch (UnsupportedEncodingException e) {
            log.error("Error during build entity. Message: {}, Exception: {}", e.getMessage(), e);
        }

        HttpPost httpPost = new HttpPost(new URL(url) + request.getRequestURI());
        httpPost.setEntity(entity);

        String responseData = healeniumBaseRequest.executeToSeleniumServer(httpPost);
        Map<String, Map<String, Object>> responseDataMap = json.toType(responseData, Json.MAP_TYPE);

        Map<String, Object> valueMap = responseDataMap.get("value");
        if (valueMap != null &&  valueMap.containsKey("error")) {
            return responseData;
        }

        String sessionId = updateCache(responseData);
        healeniumRestService.saveSessionId(sessionId);
        log.info("Report available at " + new URL(healeniumReportUrl + sessionId));
        return responseData;
    }


    private String updateCache(String responseData) {
        Map<String, Map<String, Object>> result = json.toType(responseData, Json.MAP_TYPE);
        Map<String, Object> value = result.get("value");
        String sessionId = (String) value.get("sessionId");
        SessionDelegate sessionDelegate = buildSessionDelegate(value);
        healeniumBaseRequest.getSessionDelegateCache().put(sessionId, sessionDelegate);
        return sessionId;
    }

    @SuppressWarnings("unchecked")
    private SessionDelegate buildSessionDelegate(Map<String, Object> value) {
        SessionDelegate sessionDelegate = new SessionDelegate();
        Map<String, Object> capabilities = (Map<String, Object>) value.getOrDefault("capabilities", Collections.EMPTY_MAP);
        capabilities.remove("platform");
        String url = Platform.ANDROID.equals(Platform.fromString((String) capabilities.get(PLATFORM_NAME)))
                ? appiumUrl
                : seleniumUrl;
        sessionDelegate.setCapabilities(capabilities);
        sessionDelegate.setUrl(url);
        return sessionDelegate;
    }

    public boolean isWeb(Map<String, Object> capabilities) {
        return capabilities == null || !"android".equalsIgnoreCase((String) capabilities.get(PLATFORM_NAME));
    }

}
