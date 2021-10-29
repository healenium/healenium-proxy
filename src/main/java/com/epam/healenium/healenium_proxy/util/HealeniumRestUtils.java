package com.epam.healenium.healenium_proxy.util;

import com.epam.healenium.healenium_proxy.constants.Constants;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;

import static com.epam.healenium.healenium_proxy.constants.Constants.HEALENIUM_REPORT_PATH;

@Slf4j
public class HealeniumRestUtils {

    @SneakyThrows
    public static URL getSeleniumUrl() {
        int seleniumPort = ConfigFactory.systemEnvironment().getInt("seleniumPort");
        return new URL(Constants.PROTOCOL, Constants.SELENIUM_CONTAINER_NAME, seleniumPort, Constants.SELENIUM_EXECUTOR_PATH);
    }

    @SneakyThrows
    public static URL getReportInitUrl(String sessionId) {
        Config config = ConfigFactory.systemEnvironment();
        String serverHost = config.getString("serverHost");
        int serverPort = ConfigFactory.systemEnvironment().getInt("serverPort");
        return new URL(Constants.PROTOCOL, serverHost, serverPort, HEALENIUM_REPORT_PATH + sessionId);
    }

    @SneakyThrows
    public static URL getHealeniumUrl() {
        int serverPort = ConfigFactory.systemEnvironment().getInt("serverPort");
        return new URL(Constants.PROTOCOL, Constants.HEALENIUM_CONTAINER_NAME, serverPort, "");
    }
}
