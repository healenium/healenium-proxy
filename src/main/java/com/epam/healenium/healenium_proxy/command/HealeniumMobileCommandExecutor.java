package com.epam.healenium.healenium_proxy.command;

import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import io.appium.java_client.remote.AppiumW3CHttpCommandCodec;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.codec.w3c.W3CHttpResponseCodec;

import java.lang.reflect.Field;
import java.net.URL;

@Slf4j(topic = "healenium")
public class HealeniumMobileCommandExecutor extends HealeniumCommandExecutor {

    public HealeniumMobileCommandExecutor(URL addressOfRemoteServer, String sessionId, ProxySessionContext proxySessionContext) {
        super(addressOfRemoteServer, sessionId, proxySessionContext);
    }

    @Override
    protected void updateCodec() {
        try {
            Field commandCodec;
            commandCodec = this.getClass().getSuperclass().getSuperclass().getDeclaredField("commandCodec");
            commandCodec.setAccessible(true);
            commandCodec.set(this, new AppiumW3CHttpCommandCodec());

            Field responseCodec;
            responseCodec = this.getClass().getSuperclass().getSuperclass().getDeclaredField("responseCodec");
            responseCodec.setAccessible(true);
            responseCodec.set(this, new W3CHttpResponseCodec());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Error during update codec. Message: {}, Exception: {}", e.getMessage(), e.toString());
        }
    }
}
