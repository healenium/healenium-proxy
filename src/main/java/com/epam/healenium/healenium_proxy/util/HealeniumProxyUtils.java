package com.epam.healenium.healenium_proxy.util;

public interface HealeniumProxyUtils {
    /**
     * Pattern to determining the required requests types
     * /session/any letters from a to f, any digits from 0 to 9, symbol -, length between 32 and 36/element
     */
    static final String HEALING_REGEX = "^/(session)/(([a-f0-9-]){32,36})/(element|elements)$";
    static final String WEB_ELEMENT_PATTERN =
            "{\"sessionId\":\"%s\",\"status\":0,\"value\":{\"ELEMENT\":\"%s\"}}";
    static final String SELENIUM_EXECUTOR = "http://127.0.0.1:4444/wd/hub";
}
