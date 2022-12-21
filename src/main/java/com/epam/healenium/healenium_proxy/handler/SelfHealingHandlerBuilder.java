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
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.RemoteWebDriver;

import static com.epam.healenium.SelfHealingDriver.setEngineFields;

@Slf4j
public class SelfHealingHandlerBuilder {

    public static SelfHealingHandler buildSelfHealingWebHandlerDriver(RemoteWebDriver delegate, Config config) {
        SelfHealingEngine engine = webEngine(delegate, config);
        return new BaseHandler(engine);
    }

    public static SelfHealingHandler buildSelfHealingWebHandlerWebElement(RemoteWebDriver delegate, Config config) {
        SelfHealingEngine engine = webEngine(delegate, config);
        return new WebElementProxyHandler(engine);
    }

    public static SelfHealingHandler buildSelfHealingMobileNativeHandlerDriver(RemoteWebDriver delegate, Config config) {
        SelfHealingEngine engine = mobileEngine(delegate, config);
        return new BaseHandler(engine);
    }

    public static SelfHealingHandler buildSelfHealingMobileNativeHandlerWebElement(RemoteWebDriver delegate, Config config) {
        SelfHealingEngine engine = mobileEngine(delegate, config);
        return new WebElementProxyHandler(engine);
    }

    private static SelfHealingEngine webEngine(RemoteWebDriver delegate, Config config) {
        SelfHealingEngine engine = new SelfHealingEngine(delegate, config);
        setEngineFields(delegate, engine);
        engine.getClient().setMapper(new ProxyHealeniumMapper(new MobileStackTraceReader()));
        return engine;
    }

    private static SelfHealingEngine mobileEngine(RemoteWebDriver delegate, Config config) {
        SelfHealingEngine engine = new MobileSelfHealingEngine(delegate, config);
        engine.setClient(new RestClient(engine.getConfig()));
        engine.setNodeService(new MobileNodeService(delegate));
        engine.setHealingService(new MobileHealingService(engine.getConfig(), delegate));
        engine.getClient().setMapper(new ProxyHealeniumMapper(new MobileStackTraceReader()));
        return engine;
    }

}
