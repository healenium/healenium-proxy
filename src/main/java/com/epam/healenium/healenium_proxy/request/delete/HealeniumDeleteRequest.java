package com.epam.healenium.healenium_proxy.request.delete;

import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.util.HealeniumProxyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;

@Slf4j
@Service
public class HealeniumDeleteRequest implements HealeniumHttpRequest {

    @Value("${proxy.healenium.report.url}")
    private String healeniumReportUrl;

    private final HealeniumBaseRequest healeniumBaseRequest;
    private final HealeniumProxyUtils healeniumProxyUtils;

    public HealeniumDeleteRequest(HealeniumBaseRequest healeniumBaseRequest, HealeniumProxyUtils healeniumProxyUtils) {
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.healeniumProxyUtils = healeniumProxyUtils;
    }

    @Override
    public String getType() {
        return "DELETE";
    }

    @Override
    public String execute(HttpServletRequest request) throws MalformedURLException {
        String currentSessionId = healeniumProxyUtils.getCurrentSessionId(request);
        log.info("Report available at " + new URL(healeniumReportUrl + currentSessionId));
        String url = healeniumBaseRequest.getSessionDelegateCache().get(currentSessionId).getUrl();
        HttpRequestBase httpDelete = new HttpDelete(new URL(url) + request.getRequestURI());
        return healeniumBaseRequest.executeToSeleniumServer(httpDelete);
    }
}
