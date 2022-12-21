package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.mapper.JsonMapper;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@Service
public class HealeniumFindElementsPostRequest extends HealeniumFindElementPostRequest implements HealeniumHttpPostRequest {

    public HealeniumFindElementsPostRequest(ProxyResponseConverter proxyResponseConverter,
                                            SessionContextService sessionContextService,
                                            JsonMapper jsonMapper) {
        super(proxyResponseConverter, sessionContextService, jsonMapper);
    }

    @Override
    public String getURL() {
        return "elements";
    }

    @Override
    public String execute(HttpServletRequest request) {
        return super.execute(request);
    }

    protected String findElement(By by, SelfHealingHandler selfHealingDriver, SessionContext sessionContext) {
        List<WebElement> currentWebElements = selfHealingDriver.findElements(by);
//        for (WebElement currentWebElement : currentWebElements) {
//            System.out.println("finded element: " + by + " id: " + ((RemoteWebElement) currentWebElement).getId());
//        }
        currentWebElements.forEach(e -> sessionContext.getWebElements().put(((RemoteWebElement) e).getId(), e));
        return proxyResponseConverter.generateResponse(currentWebElements);
    }
}
