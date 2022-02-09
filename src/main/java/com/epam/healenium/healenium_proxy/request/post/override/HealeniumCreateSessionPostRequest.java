package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.openqa.selenium.Platform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
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

    private final HealeniumProxyUtils proxyUtils;
    private final HealeniumBaseRequest healeniumBaseRequest;
    private final HealeniumRestService healeniumRestService;

    public HealeniumCreateSessionPostRequest(HealeniumProxyUtils proxyUtils,
                                             HealeniumBaseRequest healeniumBaseRequest,
                                             HealeniumRestService healeniumRestService) {
        this.proxyUtils = proxyUtils;
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.healeniumRestService = healeniumRestService;
    }

    @Override
    public String getURL() {
        return "session";
    }

    @Override
    public String execute(HttpServletRequest request) throws MalformedURLException, JsonProcessingException {
        String requestBody = proxyUtils.getRequestBody(request);
        HashMap bodyMap = new ObjectMapper().readValue(requestBody, HashMap.class);
        String url = isWeb((HashMap) bodyMap.get("desiredCapabilities")) ? seleniumUrl : appiumUrl;

        StringEntity entity = null;
        try {
            entity = new StringEntity(requestBody);
        } catch (UnsupportedEncodingException e) {
            log.error("Error during execute Post Request. Message: {}, Exception: {}", e.getMessage(), e);
        }

        HttpPost httpPost = new HttpPost(new URL(url) + request.getRequestURI());
        httpPost.setEntity(entity);

        String responseData = healeniumBaseRequest.executeToSeleniumServer(httpPost);

        String sessionId = updateCache(responseData);
        healeniumRestService.saveSessionId(sessionId);
        log.info("Report available at " + new URL(healeniumReportUrl + sessionId));
        return responseData;
    }


    private String updateCache(String responseData) throws JsonProcessingException, MalformedURLException {
        Map<String, Object> result = new ObjectMapper().readValue(responseData, HashMap.class);
        Map<String, Object> value = (Map<String, Object>) result.get("value");
        String sessionId = (String) value.get("sessionId");
        SessionDelegate sessionDelegate = buildSessionDelegate(value);
        healeniumBaseRequest.getSessionDelegateCache().put(sessionId, sessionDelegate);
        return sessionId;
    }

    private SessionDelegate buildSessionDelegate(Map<String, Object> value) {
        SessionDelegate sessionDelegate = new SessionDelegate();
        HashMap<String, Object> capabilities = value.get("capabilities") != null
                ? (HashMap) value.get("capabilities")
                : new HashMap<>();
        capabilities.remove("platform");
        String url = Platform.ANDROID.equals(Platform.fromString((String) capabilities.get(PLATFORM_NAME)))
                ? appiumUrl
                : seleniumUrl;
        sessionDelegate.setCapabilities(capabilities);
        sessionDelegate.setUrl(url);
        return sessionDelegate;
    }

    public boolean isWeb(HashMap<String, Object> capabilities) {
        return capabilities == null || !"android".equalsIgnoreCase((String) capabilities.get("platformName"));
    }

}
