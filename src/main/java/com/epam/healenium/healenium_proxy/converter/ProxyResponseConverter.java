package com.epam.healenium.healenium_proxy.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.ErrorCodes;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Component;

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
        JSONArray jsonArray = new JSONArray();
        currentWebElements.forEach(
                el -> {
                    JSONObject elementJsonObject = new JSONObject();
                    elementJsonObject.put(ELEMENT, ((RemoteWebElement) el).getId());
                    jsonArray.put(elementJsonObject);
                }
        );
        JSONObject finalJsonObject = new JSONObject();
        finalJsonObject.put(VALUE, jsonArray);
        return finalJsonObject.toString();
    }

    public String generateResponse(WebElement currentWebElements) {
        Gson json = new GsonBuilder().serializeNulls().create();
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
}
