package com.epam.healenium.healenium_proxy.request.post.override;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HealeniumHttpPostRequestFactory {

    @Autowired
    private List<HealeniumHttpPostRequest> postRequests;

    private static final Map<String, HealeniumHttpPostRequest> postRequestsCache = new HashMap<>();

    @PostConstruct
    public void initRequestCache() {
        for (HealeniumHttpPostRequest service : postRequests) {
            postRequestsCache.put(service.getURL(), service);
        }
    }

    public static HealeniumHttpPostRequest getRequest(String type) {
        HealeniumHttpPostRequest service = null;
        for (Map.Entry<String, HealeniumHttpPostRequest> item : postRequestsCache.entrySet()) {
            if (type.endsWith(item.getKey())) {
                service = item.getValue();
            }
        }
        return service;
    }
}
