package com.epam.healenium.healenium_proxy.command;

import io.appium.java_client.remote.AppiumW3CHttpCommandCodec;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.remote.http.W3CHttpResponseCodec;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;

@Slf4j
public class HealeniumCommandExecutor extends HttpCommandExecutor {

    private final String sessionId;

    public HealeniumCommandExecutor(URL addressOfRemoteServer, String sessionId) {
        super(addressOfRemoteServer);
        this.sessionId = sessionId;
    }

    @Override
    public Response execute(Command command) throws IOException {
        if (!command.getName().equals("newSession")) {
            return super.execute(command);
        }
        Response response = new Response();
        response.setSessionId(sessionId);
        response.setStatus(0);
        response.setValue(((DesiredCapabilities) command.getParameters().get("desiredCapabilities")).asMap());
        updateCodec();
        return response;
    }

    private void updateCodec() {
        try {
            Field commandCodec;
            commandCodec = this.getClass().getSuperclass().getDeclaredField("commandCodec");
            commandCodec.setAccessible(true);
            commandCodec.set(this, new AppiumW3CHttpCommandCodec());

            Field responseCodec;
            responseCodec = this.getClass().getSuperclass().getDeclaredField("responseCodec");
            responseCodec.setAccessible(true);
            responseCodec.set(this, new W3CHttpResponseCodec());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Error during update codec. Message: {}, Exception: {}", e.getMessage(), e);
        }
    }
}
