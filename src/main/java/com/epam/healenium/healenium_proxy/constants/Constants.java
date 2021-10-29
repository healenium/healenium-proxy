package com.epam.healenium.healenium_proxy.constants;

public class Constants {

    /**
     * Pattern to determining the required requests types
     * /session/any letters from a to f, any digits from 0 to 9, symbol -, length between 32 and 36/element
     */
    public static final String HEALING_REGEX = "^/(session)/(([a-f0-9-]){32,36})/(element|elements)$";
    public static final String HEALING_REGEX_WEB_EL =
            "^/(session)/(([a-f0-9-]){32,36})/(element)/(([a-f0-9-]){32,36})/(element|elements)$";

    public static final String PROTOCOL = "http";

    public static final String SELENIUM_CONTAINER_NAME = "hlm-selenium-standalone-xpra";
    public static final String HEALENIUM_CONTAINER_NAME = "healenium";

    public static final String SELENIUM_EXECUTOR_PATH = "/wd/hub";
    public static final String NEW_SESSION_PATH = "/wd/hub/session";
    public static final String HEALENIUM_REPORT_PATH = "/healenium/report/";

    public static final String PATTERN_WEB_ELEMENT = "{\"value\":{\"ELEMENT\":\"%s\"}}";
    public static final String PATTERN_WEB_ELEMENTS = "{\"ELEMENT\":\"%s\"}";
    public static final String PATTERN_WEB_ELEMENTS_VALUES = "{\"value\":%s}";
}
