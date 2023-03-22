package com.epam.healenium.healenium_proxy.handler;

import com.epam.healenium.SelfHealingEngine;
import com.epam.healenium.appium.MobileSelfHealingEngine;
import com.epam.healenium.appium.service.MobileHealingService;
import com.epam.healenium.appium.service.MobileNodeService;
import com.epam.healenium.appium.utils.MobileStackTraceReader;
import com.epam.healenium.client.RestClient;
import com.epam.healenium.healenium_proxy.mapper.ProxyHealeniumMapper;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.RemoteWebDriver;

import static com.epam.healenium.SelfHealingDriver.callInitActions;
import static com.epam.healenium.SelfHealingDriver.setEngineFields;

@Slf4j(topic = "healenium")
public class SelfHealingHandlerBuilder {

    public static SelfHealingEngine webEngine(RemoteWebDriver delegate, Config config) {
        SelfHealingEngine engine = new SelfHealingEngine(delegate, config);
        setEngineFields(delegate, engine);
        callInitActions(engine);
        engine.getClient().setMapper(new ProxyHealeniumMapper(new MobileStackTraceReader()));
        return engine;
    }

    public static SelfHealingEngine mobileEngine(RemoteWebDriver delegate, Config config) {
        SelfHealingEngine engine = new MobileSelfHealingEngine(delegate, config);
        engine.setClient(new RestClient(engine.getConfig()));
        engine.setNodeService(new MobileNodeService());
        engine.setHealingService(new MobileHealingService(engine.getConfig(), delegate));
        engine.getClient().setMapper(new ProxyHealeniumMapper(new MobileStackTraceReader()));
        callInitActions(engine);
        return engine;
    }

}
