package com.epam.healenium.healenium_proxy.util;

import com.epam.healenium.healenium_proxy.constants.Constants;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;

@Slf4j
@Component
public class HealeniumRestUtils {

    @Value("${proxy.selenium.container.name}")
    private String seleniumContainerName;

    @Value("${proxy.healenium.container.name}")
    private String healeniumContainerName;

    @Value("${proxy.healenium.report.path}")
    private String healeniumReportPath;

    @Value("${proxy.selenium.port}")
    private int seleniumPort;

    @SneakyThrows
    public URL getSeleniumUrl() {
        return new URL(Constants.PROXY_PROTOCOL, seleniumContainerName, seleniumPort, Constants.SELENIUM_EXECUTOR_PATH);
    }

    @SneakyThrows
    public URL getReportInitUrl(String sessionId) {
        Config config = ConfigFactory.systemEnvironment();
        String serverHost = config.getString("serverHost");
        int serverPort = ConfigFactory.systemEnvironment().getInt("serverPort");
        return new URL(Constants.PROXY_PROTOCOL, serverHost, serverPort,healeniumReportPath + sessionId);
    }

    @SneakyThrows
    public URL getHealeniumUrl() {
        int serverPort = ConfigFactory.systemEnvironment().getInt("serverPort");
        return new URL(Constants.PROXY_PROTOCOL, healeniumContainerName, serverPort, "");
    }
}
