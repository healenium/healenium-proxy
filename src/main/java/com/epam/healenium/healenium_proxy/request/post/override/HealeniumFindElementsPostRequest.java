package com.epam.healenium.healenium_proxy.request.post.override;

import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
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

    protected String getHealingResponse(WebDriver selfHealingDriver, By by) {
        List<WebElement> currentWebElements = selfHealingDriver.findElements(by);
        return proxyUtils.generateResponse(currentWebElements);
    }
}
