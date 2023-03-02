package com.epam.healenium.healenium_proxy.restore;

import com.epam.healenium.SelfHealingEngine;
import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.handlers.proxy.BaseHandler;
import com.epam.healenium.handlers.proxy.WebElementProxyHandler;
import com.epam.healenium.healenium_proxy.command.HealeniumCommandExecutor;
import com.epam.healenium.healenium_proxy.handler.SelfHealingHandlerBuilder;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.typesafe.config.Config;
import lombok.AllArgsConstructor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Service;


@Service
public class RestoreWebDriver implements RestoreDriver {

//    @Override
//    public SelfHealingHandler restoreSelfHealingHandlerDrive(String sessionId, SessionContext sessionContext, Config config) {
//        RemoteWebDriver restoreWebDriver = restoreWebDriverFromSession(sessionId, sessionContext);
//        return SelfHealingHandlerBuilder.buildSelfHealingWebHandlerDriver(restoreWebDriver, config);
//    }
//
//    @Override
//    public SelfHealingHandler restoreSelfHealingHandlerWebElement(String sessionId, SessionContext sessionContext, Config config) {
//        RemoteWebDriver restoreWebDriver = restoreWebDriverFromSession(sessionId, sessionContext);
//        return SelfHealingHandlerBuilder.buildSelfHealingWebHandlerWebElement(restoreWebDriver, config);
//    }

    @Override
    public void restoreSelfHealing(String sessionId, SessionContext sessionContext, Config config) {
        RemoteWebDriver restoreWebDriver = restoreWebDriverFromSession(sessionId, sessionContext);
        SelfHealingEngine engine = SelfHealingHandlerBuilder.webEngine(restoreWebDriver, config);
        sessionContext.setSelfHealingHandlerBase(new BaseHandler(engine));
        sessionContext.setSelfHealingHandlerWebElement(new WebElementProxyHandler(engine));
    }

    private RemoteWebDriver restoreWebDriverFromSession(String sessionId, SessionContext sessionContext) {
        CommandExecutor executor = new HealeniumCommandExecutor(sessionContext.getUrl(), sessionId, sessionContext);
        return new RemoteWebDriver(executor, new MutableCapabilities(sessionContext.getCapabilities()));
    }
}
