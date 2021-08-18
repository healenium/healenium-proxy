package com.epam.healenium.healenium_proxy.util.impl;

import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import org.json.JSONObject;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.Response;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.http.W3CHttpCommandCodec;
import org.openqa.selenium.remote.http.W3CHttpResponseCodec;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.stream.Collectors;

public class HealeniumProxyUtilsImpl implements HealeniumProxyUtils {
    public String getHealingRegex() {
        return HEALING_REGEX;
    }

    public String getWebElementPattern() {
        return WEB_ELEMENT_PATTERN;
    }

    public String getSeleniumExecutor() {
        return SELENIUM_EXECUTOR;
    }

    /**
     * Get request body from request
     * @param request
     * @return
     */
    public String getRequestBody(HttpServletRequest request) {
        String requestBody = "";
        try {
            return new BufferedReader(new InputStreamReader(request.getInputStream()))
                    .lines().collect(Collectors.joining(""));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return requestBody;
    }

    /**
     * Restore current remote webdriver
     *
     * @param commandExecutor
     * @param sessionId
     * @return
     */
    public RemoteWebDriver restoreWebDriverFromSession(URL commandExecutor, SessionId sessionId) {
        CommandExecutor executor = new HttpCommandExecutor(commandExecutor) {
            @Override
            public Response execute(Command command) throws IOException {
                Response response;
                if (command.getName().equals("newSession")) {
                    response = new Response();
                    response.setSessionId(sessionId.toString());
                    response.setStatus(0);
                    response.setValue(Collections.<String, String>emptyMap());

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
                        e.printStackTrace();
                    }
                } else {
                    response = super.execute(command);
                }
                return response;
            }
        };

        return new RemoteWebDriver(executor, new DesiredCapabilities());
    }

    public String getXpathFromRequest(String requestBody) {
        JSONObject jsonObj = new JSONObject(requestBody);
        return jsonObj.get("value").toString();
    }

    public SessionId getCurrentSessionId(HttpServletRequest request) {
        return new SessionId(request.getRequestURI().split("/")[2]);
    }
}
