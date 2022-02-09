package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.appium.wrapper.DriverWrapper;
import com.epam.healenium.healenium_proxy.command.HealeniumCommandExecutor;
import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.service.RestoreDriverService;
import com.typesafe.config.Config;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidElement;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;

@Service
public class RestoreMobileDriverServiceImpl implements RestoreDriverService {

    @Override
    public Platform getPlatformName() {
        return Platform.ANDROID;
    }

    @Override
    public WebDriver restoreDriver(String currentSessionId, SessionDelegate sessionDelegate, Config config) throws MalformedURLException {
        AndroidDriver restoreAndroidDriver = restoreAndroidDriverFromSession(currentSessionId, sessionDelegate);
        return DriverWrapper.wrap(restoreAndroidDriver, config);
    }

    private AndroidDriver<AndroidElement> restoreAndroidDriverFromSession(String currentSessionId, SessionDelegate sessionDelegate) throws MalformedURLException {
        HttpCommandExecutor executor = new HealeniumCommandExecutor(new URL(sessionDelegate.getUrl()), currentSessionId);
        return new AndroidDriver(executor, new DesiredCapabilities(sessionDelegate.getCapabilities()));
    }
}
