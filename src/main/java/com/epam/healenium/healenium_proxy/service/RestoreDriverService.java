package com.epam.healenium.healenium_proxy.service;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.typesafe.config.Config;
import org.openqa.selenium.WebElement;

import java.net.MalformedURLException;

public interface RestoreDriverService {

    SelfHealingHandler restoreSelfHealingHandler(String currentSessionId, SessionDelegate sessionDelegate, WebElement el, Config config) throws MalformedURLException;
}
