package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.SelfHealingDriver;
import com.epam.healenium.healenium_proxy.command.HealeniumCommandExecutor;
import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.service.RestoreDriverService;
import com.typesafe.config.Config;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;

@Service
public class RestoreWebDriverServiceImpl implements RestoreDriverService {

    @Override
    public Platform getPlatformName() {
        return Platform.LINUX;
    }

    @Override
    public WebDriver restoreDriver(String currentSessionId, SessionDelegate sessionDelegate, Config config) throws MalformedURLException {
        RemoteWebDriver restoreWebDriver = restoreWebDriverFromSession(currentSessionId, sessionDelegate);
        return SelfHealingDriver.create(restoreWebDriver, config);
    }

    private RemoteWebDriver restoreWebDriverFromSession(String currentSessionId, SessionDelegate sessionDelegate) throws MalformedURLException {
        CommandExecutor executor = new HealeniumCommandExecutor(new URL(sessionDelegate.getUrl()), currentSessionId, sessionDelegate);
        return new RemoteWebDriver(executor, new MutableCapabilities(sessionDelegate.getCapabilities()));
    }
}
