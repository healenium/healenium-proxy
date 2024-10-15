package com.epam.healenium.healenium_proxy.filter;

import com.epam.healenium.healenium_proxy.service.DisableHealingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j(topic = "healenium")
@Component
public class ExecuteScriptGatewayFilterFactory extends AbstractGatewayFilterFactory<ExecuteScriptGatewayFilterFactory.Config> {
    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory;
    private final DisableHealingService disableHealingService;

    public ExecuteScriptGatewayFilterFactory(ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory,
                                             DisableHealingService disableHealingService) {
        super(Config.class);
        this.modifyResponseBodyFilterFactory = modifyResponseBodyFilterFactory;
        this.disableHealingService = disableHealingService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        final ModifyResponseBodyGatewayFilterFactory.Config modifyResponseBodyFilterFactoryConfig = new ModifyResponseBodyGatewayFilterFactory.Config();
        modifyResponseBodyFilterFactoryConfig.setRewriteFunction(String.class, String.class, (exchange, originalResponseBody) -> {
            if (exchange.getResponse().getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
                log.info("[Execute Script] Original Response Body: {}", originalResponseBody);
                String disableHealingValue = disableHealingService.getDisableHealingMessageValue(originalResponseBody);
                if (disableHealingValue != null) {
                    return disableHealingService.process(exchange, disableHealingValue);
                }
            }
            return Mono.just(originalResponseBody);
        });
        return modifyResponseBodyFilterFactory.apply(modifyResponseBodyFilterFactoryConfig);
    }

    public static class Config {
    }
}
