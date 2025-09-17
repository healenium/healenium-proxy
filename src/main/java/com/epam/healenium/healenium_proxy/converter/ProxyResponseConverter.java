package com.epam.healenium.healenium_proxy.converter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.json.Json;
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

    private static final String ELEMENT = "element-6066-11e4-a52e-4f735466cecf";
    private static final String VALUE = "value";
    private static final String MESSAGE = "message";
    private static final String ERROR = "error";

    private static final String UNKNOWN_ERROR = "unknown error";
    private static final String UNKNOWN_COMMAND = "unknown command";
    private static final String SESSION_NOT_CREATED = "session not created";
    private static final String NO_SUCH_ELEMENT = "no such element";
    private static final String STALE_ELEMENT_REFERENCE = "stale element reference";

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
        String errorState = getErrorState(e);

        Map<String, String> messageErrorMap = new HashMap<>();
        messageErrorMap.put(MESSAGE, e instanceof WebDriverException
                ? ((WebDriverException) e).getRawMessage()
                : e.getMessage());
        messageErrorMap.put(ERROR, errorState);

        return json.toJson(Collections.singletonMap(VALUE, messageErrorMap));
    }

    private String getErrorState(Exception e) {
        if (e == null) {
            return UNKNOWN_ERROR;
        }
        
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        
        if (e instanceof org.openqa.selenium.NoSuchElementException) {
            return NO_SUCH_ELEMENT;
        } else if (e instanceof org.openqa.selenium.StaleElementReferenceException) {
            return STALE_ELEMENT_REFERENCE;
        } else if (e instanceof org.openqa.selenium.SessionNotCreatedException) {
            return SESSION_NOT_CREATED;
        } else if (message.contains("unknown command")) {
            return UNKNOWN_COMMAND;
        }
        
        return UNKNOWN_ERROR;
    }
}