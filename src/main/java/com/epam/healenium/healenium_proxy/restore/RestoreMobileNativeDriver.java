package com.epam.healenium.healenium_proxy.restore;

import com.epam.healenium.SelfHealingEngine;
import com.epam.healenium.handlers.proxy.BaseHandler;
import com.epam.healenium.handlers.proxy.WebElementProxyHandler;
import com.epam.healenium.healenium_proxy.command.HealeniumMobileCommandExecutor;
import com.epam.healenium.healenium_proxy.handler.SelfHealingHandlerBuilder;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.typesafe.config.Config;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Service;

@Service
public class RestoreMobileNativeDriver implements RestoreDriver {

    @Override
    public void restoreSelfHealing(String sessionId, SessionContext sessionContext, Config config) {
        RemoteWebDriver restoreWebDriver = restoreWebDriverFromSession(sessionId, sessionContext);
        SelfHealingEngine engine = SelfHealingHandlerBuilder.mobileEngine(restoreWebDriver, config);
        sessionContext.setSelfHealingHandlerBase(new BaseHandler(engine));
        sessionContext.setSelfHealingHandlerWebElement(new WebElementProxyHandler(engine));
        sessionContext.setSelfHealingEngine(engine);
    }

    private RemoteWebDriver restoreWebDriverFromSession(String sessionId, SessionContext sessionContext) {
        HttpCommandExecutor executor = new HealeniumMobileCommandExecutor(sessionContext.getUrl(), sessionId, sessionContext);
        return new AppiumDriver(executor, new DesiredCapabilities(sessionContext.getCapabilities()));
    }
}
