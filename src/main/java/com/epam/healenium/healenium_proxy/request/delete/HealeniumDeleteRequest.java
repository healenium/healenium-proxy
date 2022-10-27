package com.epam.healenium.healenium_proxy.request.delete;

import com.epam.healenium.healenium_proxy.request.HealeniumBaseRequest;
import com.epam.healenium.healenium_proxy.request.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.service.HttpServletRequestService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;

@Slf4j
@Service
public class HealeniumDeleteRequest implements HealeniumHttpRequest {

    @Value("${proxy.healenium.report.url}")
    private String healeniumReportUrl;

    private final HealeniumBaseRequest healeniumBaseRequest;
    private final HttpServletRequestService servletRequestService;

    public HealeniumDeleteRequest(HealeniumBaseRequest healeniumBaseRequest, HttpServletRequestService healeniumProxyUtils) {
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.servletRequestService = healeniumProxyUtils;
    }

    @Override
    public String getType() {
        return "DELETE";
    }

    @Override
    public String execute(HttpServletRequest request) throws IOException {
        String currentSessionId = servletRequestService.getCurrentSessionId(request);
        log.info("Report available at " + new URL(healeniumReportUrl + currentSessionId));
        String url = healeniumBaseRequest.getSessionDelegateCache().get(currentSessionId).getUrl();
        HttpRequestBase httpDelete = new HttpDelete(new URL(url) + request.getRequestURI());
        healeniumBaseRequest.getSessionDelegateCache().remove(currentSessionId);
        return healeniumBaseRequest.executeToSeleniumServer(httpDelete);
    }
}
