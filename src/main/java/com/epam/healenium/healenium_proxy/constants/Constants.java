package com.epam.healenium.healenium_proxy.constants;

public class Constants {

    private Constants() {}

    /**
     * Pattern to determining the required requests types
     * /session/any letters from a to f, any digits from 0 to 9, symbol -, length between 32 and 36/element
     */
    public static final String HEALING_REGEX = "^/(session)/(([a-f0-9-]){32,36})/(element|elements)$";

    public static final String SELENIUM_EXECUTOR = "http://127.0.0.1:4444/wd/hub";
    public static final String PATTERN_WEB_ELEMENT = "{\"value\":{\"ELEMENT\":\"%s\"}}";
    public static final String PATTERN_WEB_ELEMENTS = "{\"ELEMENT\":\"%s\"}";
    public static final String PATTERN_WEB_ELEMENTS_VALUES = "{\"value\":%s}";
}
