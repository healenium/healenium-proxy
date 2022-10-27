package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.config.ProxyConfig;
import com.epam.healenium.healenium_proxy.converter.ProxyResponseConverter;
import com.epam.healenium.healenium_proxy.model.SessionDelegate;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import com.epam.healenium.healenium_proxy.service.RestoreDriverServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.util.List;

@Slf4j
@Service
public class HealeniumFindElementsPostRequest extends HealeniumFindElementPostRequest implements HealeniumHttpPostRequest {

    public HealeniumFindElementsPostRequest(HttpServletRequestService httpServletRequestService,
                                            ProxyResponseConverter proxyResponseConverter,
                                            HealeniumBaseRequest healeniumBaseRequest,
                                            HealeniumRestService healeniumRestService,
                                            ProxyConfig proxyConfig,
                                            RestoreDriverServiceFactory restoreDriverServiceFactory) {
        super(httpServletRequestService, proxyResponseConverter, healeniumBaseRequest, healeniumRestService,
                proxyConfig, restoreDriverServiceFactory);
    }

    @Override
    public String getURL() {
        return "elements";
    }

    @Override
    public String execute(HttpServletRequest request) throws MalformedURLException {
        return super.execute(request);
    }

    protected String findElement(By by, SelfHealingHandler selfHealingDriver, SessionDelegate sessionDelegate) {
        List<WebElement> currentWebElements = selfHealingDriver.findElements(by);
        currentWebElements.forEach(e -> sessionDelegate.getWebElements().put(((RemoteWebElement) e).getId(), e));
        return proxyResponseConverter.generateResponse(currentWebElements);
    }
}
