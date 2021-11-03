package com.epam.healenium.healenium_proxy.util;

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

    private static String protocol;
    private static String seleniumContainerName;
    private static String seleniumExecutorPath;
    private static String healeniumContainerName;
    private static String healeniumReportPath;

    public HealeniumRestUtils (@Value("${proxy.protocol}") String protocol,
                               @Value("${proxy.selenium.container.name}") String seleniumContainerName,
                               @Value("${proxy.selenium.executor.path}") String seleniumExecutorPath,
                               @Value("${proxy.healenium.container.name}") String healeniumContainerName,
                               @Value("${proxy.healenium.report.path}") String healeniumReportPath) {
        HealeniumRestUtils.protocol = protocol;
        HealeniumRestUtils.seleniumContainerName = seleniumContainerName;
        HealeniumRestUtils.seleniumExecutorPath = seleniumExecutorPath;
        HealeniumRestUtils.healeniumContainerName = healeniumContainerName;
        HealeniumRestUtils.healeniumReportPath = healeniumReportPath;
    }

    @SneakyThrows
    public static URL getSeleniumUrl() {
        int seleniumPort = ConfigFactory.systemEnvironment().getInt("seleniumPort");
        return new URL(protocol, seleniumContainerName, seleniumPort, seleniumExecutorPath);
    }

    @SneakyThrows
    public static URL getReportInitUrl(String sessionId) {
        Config config = ConfigFactory.systemEnvironment();
        String serverHost = config.getString("serverHost");
        int serverPort = ConfigFactory.systemEnvironment().getInt("serverPort");
        return new URL(protocol, serverHost, serverPort,healeniumReportPath + sessionId);
    }

    @SneakyThrows
    public static URL getHealeniumUrl() {
        int serverPort = ConfigFactory.systemEnvironment().getInt("serverPort");
        return new URL(protocol, healeniumContainerName, serverPort, "");
    }
}
