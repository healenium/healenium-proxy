package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.handlers.proxy.WebElementProxyHandler;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.restore.RestoreDriver;
import com.epam.healenium.healenium_proxy.restore.RestoreDriverFactory;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "healenium")
@Service
public class SessionContextService {

    @Value("${proxy.selenium.url}")
    private String seleniumUrl;

    @Value("${proxy.appium.url}")
    private String appiumUrl;

    @Value("${proxy.healenium.server.url}")
    private String healeniumServerUrl;

    private static final String HEALENIUM_REPORT_PATH = "/healenium/report/";

    @Getter
    private PassiveExpiringMap<String, SessionContext> sessionContextCache = new PassiveExpiringMap<>(8, TimeUnit.HOURS);

    private final RestoreDriverFactory restoreDriverFactory;
    private final HttpServletRequestService servletRequestService;
    private final ProxyConfig proxyConfig;
    private final JsonMapper jsonMapper;

    public SessionContextService(RestoreDriverFactory restoreDriverFactory,
                                 HttpServletRequestService servletRequestService,
                                 ProxyConfig proxyConfig,
                                 JsonMapper jsonMapper) {
        this.restoreDriverFactory = restoreDriverFactory;
        this.servletRequestService = servletRequestService;
        this.proxyConfig = proxyConfig;
        this.jsonMapper = jsonMapper;
    }

    @SneakyThrows
    public SessionContext initSessionContext(HttpServletRequest request) {
        URL url;
        String requestBody = servletRequestService.getRequestBody(request);
        SessionContext sessionContext = new SessionContext()
                .setCreateSessionReqBody(requestBody);
        boolean isAppium = jsonMapper.isAppium(requestBody);
        try {
            url = isAppium ? new URL(appiumUrl) : new URL(seleniumUrl);
        } catch (MalformedURLException e) {
            url = new URL("http://localhost:4444/");
            log.error("Error create selenium server url: {}", e.getMessage());
            log.error("Connect to selenium server: {}", url);
        }
        HttpClient httpClient = servletRequestService.getHttpClient(url);
        return sessionContext
                .setUrl(url)
                .setHttpClient(httpClient);
    }

    public void fillRestoreSelfHealingHandlers(String currentSessionId, SessionContext sessionContext) {
        RestoreDriver restoreDriver = restoreDriverFactory.getRestoreService(sessionContext.getCapabilities());
        restoreDriver.restoreSelfHealing(currentSessionId, sessionContext, proxyConfig.getConfig(currentSessionId));
    }

    public String submitSessionContext(String responseData, SessionContext sessionContext) {
        String sessionId = enrichSessionContext(responseData, sessionContext);
        sessionContextCache.put(sessionId, sessionContext);
        log.info("Report available at: {}{}{}", healeniumServerUrl, HEALENIUM_REPORT_PATH, sessionId);
        return sessionId;
    }

    public String enrichSessionContext(String responseData, SessionContext sessionContext) {
        Map<String, Object> value = jsonMapper.getValue(responseData);
        sessionContext.setCapabilities(jsonMapper.getCapabilities(value));
        String sessionId = (String) value.get("sessionId");
        fillRestoreSelfHealingHandlers(sessionId, sessionContext);
        return sessionId;
    }

    public SessionContext getSessionContext(HttpServletRequest request) {
        String currentSessionId = servletRequestService.getCurrentSessionId(request);
        return sessionContextCache.get(currentSessionId);
    }

    public SelfHealingHandler getSelfHealingDriver(String id, SessionContext sessionContext) {
        WebElement el = sessionContext.getWebElements().get(id);
        return el != null
                ? ((WebElementProxyHandler) sessionContext.getSelfHealingHandlerWebElement()).setDelegate(el)
                : sessionContext.getSelfHealingHandlerBase();
    }

    public void deleteSessionContextFromCache(String currentSessionId) {
        log.info("Report available at: {}{}{}", healeniumServerUrl, HEALENIUM_REPORT_PATH, currentSessionId);
        sessionContextCache.remove(currentSessionId);
    }
}
