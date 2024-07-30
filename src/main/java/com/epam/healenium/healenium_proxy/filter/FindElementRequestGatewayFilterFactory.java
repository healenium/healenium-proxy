package com.epam.healenium.healenium_proxy.filter;

import com.epam.healenium.handlers.SelfHealingHandler;

import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.epam.healenium.healenium_proxy.service.ProxyFilterService;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;

@Slf4j(topic = "healenium")
@Component
public class FindElementRequestGatewayFilterFactory extends AbstractGatewayFilterFactory<FindElementRequestGatewayFilterFactory.Config> {

    private final SessionContextService sessionContextService;
    private final ProxyFilterService proxyFilterService;
    private final ProxyResponseConverter proxyResponseConverter;

    public static class Config {
    }

    public FindElementRequestGatewayFilterFactory(SessionContextService sessionContextService,
                                                  ProxyFilterService proxyFilterService,
                                                  ProxyResponseConverter proxyResponseConverter) {
        super(Config.class);
        this.sessionContextService = sessionContextService;
        this.proxyFilterService = proxyFilterService;
        this.proxyResponseConverter = proxyResponseConverter;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    String response;
                    try {
                        By by = proxyFilterService.getBy(dataBuffer);
                        String sessionId = proxyFilterService.getUriVariable(exchange, "session_id");
                        String parentElementId = proxyFilterService.getUriVariable(exchange, "parent_element_id");

                        response = findElement(by, sessionId, parentElementId);

                        log.info("Find Element Response: {}", response);
                        return proxyFilterService.getResponse(exchange.getResponse(), response);
                    } catch (Exception exception) {
                        return proxyFilterService.getResponse(exchange.getResponse(), exception);
                    }
                });
    }

    private String findElement(By by, String sessionId, String parentElementId) {
        ProxySessionContext proxySessionContext = sessionContextService.getSessionContext(sessionId);
        SelfHealingHandler selfHealingDriver = sessionContextService.getSelfHealingDriver(parentElementId, proxySessionContext);

        WebElement currentWebElement = selfHealingDriver.findElement(by);
        proxySessionContext.getWebElements().put(((RemoteWebElement) currentWebElement).getId(), currentWebElement);
        return proxyResponseConverter.generateResponse(currentWebElement);
    }

}