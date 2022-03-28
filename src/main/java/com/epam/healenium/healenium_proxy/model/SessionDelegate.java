package com.epam.healenium.healenium_proxy.model;

import lombok.Data;
import org.openqa.selenium.WebElement;

import java.util.HashMap;
import java.util.Map;

@Data
public class SessionDelegate {

    private HashMap<String, Object> capabilities;
    private String url;
    private Map<String, WebElement> webElements = new HashMap<>();

}
