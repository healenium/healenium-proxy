package com.epam.healenium.healenium_proxy.model;

import com.epam.healenium.SelfHealingEngine;
import com.epam.healenium.handlers.SelfHealingHandler;
import lombok.Data;
import lombok.experimental.Accessors;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.http.HttpClient;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ProxySessionContext {

    private Map<String, Object> capabilities;
    private URL url;
    private Map<String, WebElement> webElements = new HashMap<>();
    private SelfHealingHandler selfHealingHandlerBase;
    private SelfHealingHandler selfHealingHandlerWebElement;
    private HttpClient httpClient;
    private CommandExecutor commandExecutor;
    private String createSessionReqBody;
    private Map<String, List<String>> storedSelectors = new HashMap<>();
    private SelfHealingEngine selfHealingEngine;

}
