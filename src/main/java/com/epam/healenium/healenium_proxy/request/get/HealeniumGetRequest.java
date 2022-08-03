package com.epam.healenium.healenium_proxy.request.get;

import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;

@Slf4j
@Service
public class HealeniumGetRequest implements HealeniumHttpRequest {

    @Value("${proxy.selenium.url}")
    private String seleniumUrl;

    private final HealeniumBaseRequest healeniumBaseRequest;
    private final HttpServletRequestService servletRequestService;

    public HealeniumGetRequest(HealeniumBaseRequest healeniumBaseRequest, HttpServletRequestService healeniumProxyUtils) {
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.servletRequestService = healeniumProxyUtils;
    }

    @Override
    public String getType() {
        return "GET";
    }

    @Override
    public String execute(HttpServletRequest request) throws IOException {
        String currentSessionId = servletRequestService.getCurrentSessionId(request);
        String url = currentSessionId != null
                ? healeniumBaseRequest.getSessionDelegateCache().get(currentSessionId).getUrl()
                : seleniumUrl;
        HttpRequestBase httpGet = new HttpGet(new URL(url) + request.getRequestURI());
        return healeniumBaseRequest.executeToSeleniumServer(httpGet);
    }
}
