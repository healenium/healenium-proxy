package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.handlers.proxy.WebElementProxyHandler;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.epam.healenium.healenium_proxy.restore.RestoreDriver;
import com.epam.healenium.healenium_proxy.restore.RestoreDriverFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.ObjectUtils;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "healenium")
@Service
public class SessionContextService {

    @Value("${proxy.selenium.url}")
    private String seleniumUrl;

    private PassiveExpiringMap<String, ProxySessionContext> sessionContextCache = new PassiveExpiringMap<>(8, TimeUnit.HOURS);

    private final RestoreDriverFactory restoreDriverFactory;
    private final ProxyConfig proxyConfig;
    private final JsonMapper jsonMapper;

    public SessionContextService(RestoreDriverFactory restoreDriverFactory, ProxyConfig proxyConfig, JsonMapper jsonMapper) {
        this.restoreDriverFactory = restoreDriverFactory;
        this.proxyConfig = proxyConfig;
        this.jsonMapper = jsonMapper;
    }

    public ProxySessionContext initSessionContext(String request) {
        String url = seleniumUrl;
        ProxySessionContext sessionContext = getDefaultSessionContext(url);
        log.info("[Proxy] Using Selenium server: {}", url);
        return sessionContext.setCreateSessionReqBody(request);
    }

    public ProxySessionContext getDefaultSessionContext() {
        log.warn("[Proxy] Using default Selenium by URL: {}", seleniumUrl);
        return getDefaultSessionContext(seleniumUrl);
    }

    private ProxySessionContext getDefaultSessionContext(String urlStr) {
        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            log.error("[Proxy] Error create selenium server url: {}", e.getMessage());
        }
        return new ProxySessionContext()
                .setUrl(url);
    }

    public void fillRestoreSelfHealingHandlers(String currentSessionId, ProxySessionContext sessionContext) {
        RestoreDriver restoreDriver = restoreDriverFactory.getRestoreService(sessionContext.getCapabilities());
        restoreDriver.restoreSelfHealing(currentSessionId, sessionContext, proxyConfig.getConfig(currentSessionId));
    }

    public String submitSessionContext(String responseData, ProxySessionContext sessionContext) {
        String sessionId = enrichSessionContext(responseData, sessionContext);
        sessionContextCache.put(sessionId, sessionContext);
        return sessionId;
    }

    public String enrichSessionContext(String responseData, ProxySessionContext sessionContext) {
        Map<String, Object> value = jsonMapper.getValue(responseData);
        sessionContext.setCapabilities(jsonMapper.getCapabilities(value));
        String sessionId = (String) value.get("sessionId");
        fillRestoreSelfHealingHandlers(sessionId, sessionContext);
        return sessionId;
    }

    public ProxySessionContext getSessionContext(String currentSessionId) {
        ProxySessionContext proxySessionContext = sessionContextCache.get(currentSessionId);
        if (proxySessionContext == null) {
            throw new RuntimeException("[Proxy] Session context not found: " + currentSessionId);
        }
        return proxySessionContext;
    }

    public SelfHealingHandler getSelfHealingDriver(String id, ProxySessionContext sessionContext) {
        WebElement el = sessionContext.getWebElements().get(id);
        return el != null
                ? ((WebElementProxyHandler) sessionContext.getSelfHealingHandlerWebElement()).setDelegate(el)
                : sessionContext.getSelfHealingHandlerBase();
    }

    public void deleteSessionContextFromCache(String currentSessionId) {
        log.debug("[Delete Session] Delete SessionContext. Id: {}", currentSessionId);
        sessionContextCache.remove(currentSessionId);
    }
}