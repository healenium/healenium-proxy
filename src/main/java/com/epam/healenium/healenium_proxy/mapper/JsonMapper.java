package com.epam.healenium.healenium_proxy.mapper;

import com.google.common.collect.ImmutableMap;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.remote.MobilePlatform;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.openqa.selenium.json.Json.MAP_TYPE;

@Slf4j(topic = "healenium")
@Service
public class JsonMapper {

    private static final String VALUE = "value";
    private static final String ERROR = "error";
    private static final String CAPABILITIES = "capabilities";
    private static final String ALWAYSMATCH = "alwaysMatch";
    private static final String FIRSTMATCH = "firstMatch";

    public static final Map<String, Function<String, By>> BY_MAP_ELEMENT =
            ImmutableMap.<String, Function<String, By>>builder()
                    .put("css selector", By::cssSelector)
                    .put("link text", By::linkText)
                    .put("partial link text", By::partialLinkText)
                    .put("xpath", By::xpath)
                    .put("class name", By::className)
                    .put("id", By::id)
                    .put("name", By::name)
                    .put("tag name", By::tagName)
                    .put("accessibility id", AppiumBy::accessibilityId)
                    .put("-custom", AppiumBy::custom)
                    .put("-image", AppiumBy::image)
                    .put("class", By::className)
                    .build();

    private final Json json = new Json();

    public boolean isAppium(String requestBody) {
        Map<String, Object> bodyMap = json.toType(requestBody, MAP_TYPE);
        Map<String, Object> capabilities = getCapabilities(bodyMap);
        return isMobile(capabilities);
    }

    public boolean isErrorResponse(String responseData) {
        Map<String, Object> valueMap = getValue(responseData);
        return valueMap != null && valueMap.containsKey(ERROR);
    }

    public Map<String, Object> convertRequest(String requestBody) {
        return json.toType(requestBody, MAP_TYPE);
    }

    public Map<String, Object> getValue(String responseData) {
        Map<String, Map<String, Object>> result = json.toType(responseData, Json.MAP_TYPE);
        return result.get(VALUE);
    }

    public Map<String, Object> getCapabilities(Map<String, Object> value) {
        Map<String, Object> capabilities = (Map<String, Object>) value.getOrDefault(CAPABILITIES, Collections.EMPTY_MAP);
        return capabilities;
    }

    public By getBy(Map<String, Object> requestBodyMap) {
        return BY_MAP_ELEMENT.get((String) requestBodyMap.get("using"))
                .apply((String) requestBodyMap.get("value"));
    }

    public boolean isMobile(Map<String, Object> capabilities) {
        boolean isMobile = false;
        Object alwaysMatch = capabilities.get(ALWAYSMATCH);
        if (alwaysMatch != null) {
            isMobile = MobilePlatform.ANDROID.equalsIgnoreCase((String) ((Map<?, ?>) alwaysMatch).get(CapabilityType.PLATFORM_NAME))
                    || MobilePlatform.IOS.equalsIgnoreCase((String) ((Map<?, ?>) alwaysMatch).get(CapabilityType.PLATFORM_NAME));
        }
        if (!isMobile) {
            Object firstMatch = capabilities.get(FIRSTMATCH);
            if (firstMatch != null) {
                isMobile = ((List) firstMatch).stream()
                        .flatMap(lm -> ((Map) lm).entrySet().stream())
                        .anyMatch(c -> CapabilityType.PLATFORM_NAME.equals(((Map.Entry<?, ?>) c).getKey())
                                && (MobilePlatform.ANDROID.equalsIgnoreCase((String) ((Map.Entry<?, ?>) c).getValue())
                                || MobilePlatform.IOS.equalsIgnoreCase((String) ((Map.Entry<?, ?>) c).getValue())));
            }
        }
        return isMobile;
    }
}
