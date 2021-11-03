package com.epam.healenium.healenium_proxy.constants;

public class Constants {

    public static final String HEALING_REGEX = "^/(session)/(([a-f0-9-]){32,36})/(element|elements)$";
    public static final String HEALING_REGEX_WEB_EL =
            "^/(session)/(([a-f0-9-]){32,36})/(element)/(([a-f0-9-]){32,36})/(element|elements)$";

    public static final String SELENIUM_EXECUTOR_PATH = "/wd/hub";
    public static final String PROXY_PROTOCOL = "http";
    public static final String PROXY_NEW_SESSION_PATH = "/wd/hub/session";

    public static final String PATTERN_WEB_ELEMENT = "{\"value\":{\"ELEMENT\":\"%s\"}}";
    public static final String PATTERN_WEB_ELEMENTS = "{\"ELEMENT\":\"%s\"}";
    public static final String PATTERN_WEB_ELEMENTS_VALUES = "{\"value\":%s}";
}
