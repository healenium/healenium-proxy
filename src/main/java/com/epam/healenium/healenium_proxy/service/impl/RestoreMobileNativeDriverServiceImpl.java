package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.command.HealeniumMobileCommandExecutor;
import com.epam.healenium.healenium_proxy.handler.MobileSelfHealingHandler;
import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.service.RestoreDriverService;
import com.typesafe.config.Config;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;

@Service
public class RestoreMobileNativeDriverServiceImpl implements RestoreDriverService {

    @Override
    public SelfHealingHandler restoreSelfHealingHandler(String currentSessionId, SessionDelegate sessionDelegate, WebElement el, Config config) throws MalformedURLException {
        AppiumDriver restoreAndroidDriver = restoreAndroidDriverFromSession(currentSessionId, sessionDelegate);
        return MobileSelfHealingHandler.createSelfHealingMobileNativeAppHandler(restoreAndroidDriver, el, config);
    }

    private AppiumDriver restoreAndroidDriverFromSession(String currentSessionId, SessionDelegate sessionDelegate) throws MalformedURLException {
        HttpCommandExecutor executor = new HealeniumMobileCommandExecutor(new URL(sessionDelegate.getUrl()), currentSessionId, sessionDelegate);
        return new AppiumDriver(executor, new DesiredCapabilities(sessionDelegate.getCapabilities()));
    }
}
