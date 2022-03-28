package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.handlers.proxy.SelfHealingProxyInvocationHandler;
import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.util.List;

@Slf4j
@Service
public class HealeniumFindElementsPostRequest extends HealeniumFindElementPostRequest implements HealeniumHttpPostRequest {

    public HealeniumFindElementsPostRequest(HealeniumProxyUtils proxyUtils,
                                            HealeniumBaseRequest healeniumBaseRequest,
                                            HealeniumRestService healeniumRestService) {
        super(proxyUtils, healeniumBaseRequest, healeniumRestService);
    }

    @Override
    public String getURL() {
        return "elements";
    }

    @Override
    public String execute(HttpServletRequest request) throws MalformedURLException, JsonProcessingException {
        return super.execute(request);
    }

    protected String getHealingResponse(WebDriver selfHealingDriver, By by, String id, SessionDelegate sessionDelegate) {
        List<WebElement> currentWebElements;
        if (id != null) {
            WebElement el = sessionDelegate.getWebElements().get(id);
            WebElement wrapEl = ((SelfHealingProxyInvocationHandler) Proxy.getInvocationHandler(selfHealingDriver))
                    .wrapElement(el, selfHealingDriver.getClass().getClassLoader());
            currentWebElements = wrapEl.findElements(by);
        } else {
            currentWebElements = selfHealingDriver.findElements(by);
        }
        currentWebElements.forEach(e -> sessionDelegate.getWebElements().put(((RemoteWebElement) e).getId(), e));
        return proxyUtils.generateResponse(currentWebElements);
    }
}
