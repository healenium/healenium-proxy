package com.epam.healenium.healenium_proxy.restore;

import com.epam.healenium.SelfHealingEngine;
import com.epam.healenium.handlers.proxy.BaseHandler;
import com.epam.healenium.handlers.proxy.WebElementProxyHandler;
import com.epam.healenium.healenium_proxy.auth.RestClientTenantAuth;
import com.epam.healenium.healenium_proxy.command.HealeniumCommandExecutor;
import com.epam.healenium.healenium_proxy.handler.SelfHealingHandlerBuilder;
import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.typesafe.config.Config;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.epam.healenium.SelfHealingDriver.callInitActions;


@Service
public class RestoreWebDriver implements RestoreDriver {

    @Value("${healenium.m2m.internal-token:}")
    private String internalToken;

    @Override
    public void restoreSelfHealing(String sessionId, ProxySessionContext proxySessionContext, Config config) {
        RemoteWebDriver restoreWebDriver = restoreWebDriverFromSession(sessionId, proxySessionContext);
        SelfHealingEngine engine = SelfHealingHandlerBuilder.webEngine(restoreWebDriver, config);
        proxySessionContext.setSelfHealingHandlerBase(new BaseHandler(engine));
        proxySessionContext.setSelfHealingHandlerWebElement(new WebElementProxyHandler(engine));
        RestClientTenantAuth.apply(engine.getClient(), proxySessionContext.getTenantId(), internalToken);
        proxySessionContext.setSelfHealingEngine(engine);
        callInitActions(engine);
    }

    private RemoteWebDriver restoreWebDriverFromSession(String sessionId, ProxySessionContext proxySessionContext) {
        CommandExecutor executor = new HealeniumCommandExecutor(proxySessionContext.getUrl(), sessionId, proxySessionContext);
        return new RemoteWebDriver(executor, new MutableCapabilities(proxySessionContext.getCapabilities()));
    }
}
