package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.typesafe.config.Config;
import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;

import java.net.MalformedURLException;

public interface RestoreDriverService {

    Platform getPlatformName();

    WebDriver restoreDriver(String currentSessionId, SessionDelegate sessionDelegate, Config config) throws MalformedURLException;
}
