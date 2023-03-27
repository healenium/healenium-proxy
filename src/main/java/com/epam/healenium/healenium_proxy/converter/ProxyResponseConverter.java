package com.epam.healenium.healenium_proxy.converter;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.ErrorCodes;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "healenium")
@Component
public class ProxyResponseConverter {

    private final Json json = new Json();
    private final ErrorCodes errorCodes = new ErrorCodes();

    private static final String ELEMENT = "element-6066-11e4-a52e-4f735466cecf";
    private static final String VALUE = "value";
    private static final String MESSAGE = "message";
    private static final String ERROR = "error";

    public String generateResponse(List<WebElement> currentWebElements) {
        List<Object> list = new ArrayList<>();
        currentWebElements.forEach(
                el -> list.add(Collections.singletonMap(ELEMENT, ((RemoteWebElement) el).getId()))
        );
        return json.toJson(Collections.singletonMap(VALUE, list));
    }

    public String generateResponse(WebElement currentWebElements) {
        return json.toJson(Collections.singletonMap(VALUE, Collections.singletonMap(ELEMENT, ((RemoteWebElement) currentWebElements).getId())));
    }

    public String generateResponse(Exception e) {
        int statusCode = errorCodes.toStatusCode(e);
        String state = errorCodes.toState(statusCode);

        Map<String, String> messageErrorMap = new HashMap<>();
        messageErrorMap.put(MESSAGE, e instanceof WebDriverException
                ? ((WebDriverException) e).getRawMessage()
                : e.getMessage());
        messageErrorMap.put(ERROR, state);

        return json.toJson(Collections.singletonMap(VALUE, messageErrorMap));
    }

    public int getHttpStatusCode(Exception e) {
        return errorCodes.getHttpStatusCode(e);
    }
}
