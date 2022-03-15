package com.epam.healenium.healenium_proxy.request.get;

import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;

@Slf4j
@Service
public class HealeniumGetRequest implements HealeniumHttpRequest {

    private final HealeniumBaseRequest healeniumBaseRequest;
    private final HealeniumProxyUtils healeniumProxyUtils;

    public HealeniumGetRequest(HealeniumBaseRequest healeniumBaseRequest, HealeniumProxyUtils healeniumProxyUtils) {
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.healeniumProxyUtils = healeniumProxyUtils;
    }

    @Override
    public String getType() {
        return "GET";
    }

    @Override
    public String execute(HttpServletRequest request) throws MalformedURLException {
        String currentSessionId = healeniumProxyUtils.getCurrentSessionId(request);
        String url = healeniumBaseRequest.getSessionDelegateCache().get(currentSessionId).getUrl();
        HttpRequestBase httpGet = new HttpGet(new URL(url) + request.getRequestURI());
        return healeniumBaseRequest.executeToSeleniumServer(httpGet);
    }
}
