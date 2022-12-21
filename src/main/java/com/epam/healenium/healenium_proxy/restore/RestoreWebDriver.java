package com.epam.healenium.healenium_proxy.restore;

import com.epam.healenium.handlers.SelfHealingHandler;
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


@AllArgsConstructor
@Service
public class RestoreWebDriver implements RestoreDriver {

    private final HealeniumRestService restService;

    @Override
    public SelfHealingHandler restoreSelfHealingHandlerDrive(String sessionId, SessionContext sessionContext, Config config) {
        RemoteWebDriver restoreWebDriver = restoreWebDriverFromSession(sessionId, sessionContext);
        return SelfHealingHandlerBuilder.buildSelfHealingWebHandlerDriver(restoreWebDriver, config);
    }

    @Override
    public SelfHealingHandler restoreSelfHealingHandlerWebElement(String sessionId, SessionContext sessionContext, Config config) {
        RemoteWebDriver restoreWebDriver = restoreWebDriverFromSession(sessionId, sessionContext);
        return SelfHealingHandlerBuilder.buildSelfHealingWebHandlerWebElement(restoreWebDriver, config);
    }

    private RemoteWebDriver restoreWebDriverFromSession(String sessionId, SessionContext sessionContext) {
//        restService.restoreSession(sessionContext.getUrl(), sessionId, sessionContext.getCapabilities());
        CommandExecutor executor = new HealeniumCommandExecutor(sessionContext.getUrl(), sessionId, sessionContext);
        return new RemoteWebDriver(executor, new MutableCapabilities(sessionContext.getCapabilities()));
    }
}
