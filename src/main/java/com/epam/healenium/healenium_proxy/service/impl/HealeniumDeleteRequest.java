package com.epam.healenium.healenium_proxy.service.impl;

import com.epam.healenium.healenium_proxy.service.HealeniumHttpRequest;
import com.epam.healenium.healenium_proxy.util.HealeniumRestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpRequestBase;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.net.URL;

@Slf4j
@Service
public class HealeniumDeleteRequest implements HealeniumHttpRequest {

    private final HealeniumBaseRequest healeniumBaseRequest;
    private final HealeniumRestUtils healeniumRestUtils;

    public HealeniumDeleteRequest(HealeniumBaseRequest healeniumBaseRequest, HealeniumRestUtils healeniumRestUtils) {
        this.healeniumBaseRequest = healeniumBaseRequest;
        this.healeniumRestUtils = healeniumRestUtils;
    }

    @Override
    public String getType() {
        return "DELETE";
    }

    @Override
    public String execute(String uri, HttpServletRequest request) {
        String[] split = request.getRequestURI().split("/");
        URL reportInitUrl = healeniumRestUtils.getReportInitUrl(split[2]);
        log.info("Report available at " + reportInitUrl);
        HttpRequestBase httpDelete = new HttpDelete(uri);
        return healeniumBaseRequest.executeBaseRequest(httpDelete);
    }
}
