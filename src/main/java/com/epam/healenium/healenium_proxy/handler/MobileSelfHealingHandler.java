package com.epam.healenium.healenium_proxy.handler;

import com.epam.healenium.SelfHealingEngine;
import com.epam.healenium.appium.MobileSelfHealingEngine;
import com.epam.healenium.appium.service.MobileHealingService;
import com.epam.healenium.appium.service.MobileNodeService;
import com.epam.healenium.appium.utils.MobileStackTraceReader;
import com.epam.healenium.client.RestClient;
import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.handlers.proxy.BaseHandler;
import com.epam.healenium.handlers.proxy.WebElementProxyHandler;
import com.epam.healenium.healenium_proxy.mapper.ProxyHealeniumMapper;
import com.typesafe.config.Config;
import io.appium.java_client.AppiumDriver;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import static com.epam.healenium.SelfHealingDriver.setEngineFields;

@Slf4j
public class MobileSelfHealingHandler {

    public static SelfHealingHandler createSelfHealingWebHandler(RemoteWebDriver delegate, WebElement el, Config config) {
        SelfHealingEngine engine = new SelfHealingEngine(delegate, config);
        setEngineFields(delegate, engine);
        engine.getClient().setMapper(new ProxyHealeniumMapper(new MobileStackTraceReader()));
        return el == null ? new BaseHandler(engine) : new WebElementProxyHandler(el, engine);
    }

    public static SelfHealingHandler createSelfHealingMobileNativeAppHandler(AppiumDriver delegate, WebElement el, Config config) {
        SelfHealingEngine engine = new MobileSelfHealingEngine(delegate, config);
        engine.setClient(new RestClient(engine.getConfig()));
        engine.setNodeService(new MobileNodeService(delegate));
        engine.setHealingService(new MobileHealingService(engine.getConfig(), delegate));
        engine.getClient().setMapper(new ProxyHealeniumMapper(new MobileStackTraceReader()));
        return el == null ? new BaseHandler(engine) : new WebElementProxyHandler(el, engine);
    }

}
