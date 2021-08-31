package com.epam.healenium.healenium_proxy.command;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.http.W3CHttpCommandCodec;
import org.openqa.selenium.remote.http.W3CHttpResponseCodec;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;

@Slf4j
public class HealeniumCommandExecutor extends HttpCommandExecutor {

    private SessionId sessionId;

    public HealeniumCommandExecutor(URL addressOfRemoteServer, SessionId sessionId) {
        super(addressOfRemoteServer);
        this.sessionId = sessionId;
    }

    @Override
    public Response execute(Command command) throws IOException {
        if (!command.getName().equals("newSession")) {
            return super.execute(command);
        }
        Response response = new Response();
        response.setSessionId(sessionId.toString());
        response.setStatus(0);
        response.setValue(Collections.<String, String>emptyMap());
        updateCodec();
        return response;
    }

    private void updateCodec() {
        try {
            Field commandCodec;
            commandCodec = this.getClass().getSuperclass().getDeclaredField("commandCodec");
            commandCodec.setAccessible(true);
            commandCodec.set(this, new W3CHttpCommandCodec());

            Field responseCodec;
            responseCodec = this.getClass().getSuperclass().getDeclaredField("responseCodec");
            responseCodec.setAccessible(true);
            responseCodec.set(this, new W3CHttpResponseCodec());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Error during update codec. Message: {}, Exception: {}", e.getMessage(), e);
        }
    }
}
