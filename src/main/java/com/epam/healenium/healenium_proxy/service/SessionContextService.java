package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.handlers.proxy.WebElementProxyHandler;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.restore.RestoreDriver;
import com.epam.healenium.healenium_proxy.restore.RestoreDriverFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
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
        String requestBody = servletRequestService.getRequestBody(request);
        boolean isAppium = jsonMapper.isAppium(requestBody);
        String url = isAppium ? appiumUrl : seleniumUrl;
        SessionContext sessionContext = getDefaultSessionContext(url);
        log.info("[Proxy] Using Selenium server: {}", url);
        return sessionContext.setCreateSessionReqBody(requestBody);
    }

    @SneakyThrows
    public SessionContext getDefaultSessionContext() {
        log.warn("[Proxy] Using default Selenium by URL: {}", seleniumUrl);
        return getDefaultSessionContext(seleniumUrl);
    }

    private SessionContext getDefaultSessionContext(String urlStr) throws MalformedURLException {
        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            url = new URL("http://localhost:4444/");
            log.error("[Proxy] Error create selenium server url: {}", e.getMessage());
            log.error("[Proxy] Connect to selenium server: {}", url);
        }
        HttpClient httpClient = servletRequestService.getHttpClient(url);
        return new SessionContext()
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
        log.debug("[Create Session] Add SessionContext to cache. Id: {}, SC: {}", sessionId, sessionContext);
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
        return getSessionContext(currentSessionId);
    }

    public SessionContext getSessionContext(String currentSessionId) {
        SessionContext sessionContext = sessionContextCache.get(currentSessionId);
        if (sessionContext == null) {
            sessionContext = getDefaultSessionContext();
        }
        return sessionContext;

    }

    public SelfHealingHandler getSelfHealingDriver(String id, SessionContext sessionContext) {
        WebElement el = sessionContext.getWebElements().get(id);
        return el != null
                ? ((WebElementProxyHandler) sessionContext.getSelfHealingHandlerWebElement()).setDelegate(el)
                : sessionContext.getSelfHealingHandlerBase();
    }

    public void deleteSessionContextFromCache(String currentSessionId) {
        log.debug("[Delete Session] Delete SessionContext from cache. Id: {}", currentSessionId);
        sessionContextCache.remove(currentSessionId);
    }
}
