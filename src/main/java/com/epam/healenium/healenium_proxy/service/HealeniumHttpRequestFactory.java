package com.epam.healenium.healenium_proxy.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealeniumHttpRequestFactory {

    @Autowired
    private List<HealeniumHttpRequest> healeniumHttpRequests;

    private static final Map<String, HealeniumHttpRequest> healeniumHttpRequestsCache = new HashMap<>();

    @PostConstruct
    public void initMyServiceCache() {
        for(HealeniumHttpRequest service : healeniumHttpRequests) {
            healeniumHttpRequestsCache.put(service.getType(), service);
        }
    }

    public static HealeniumHttpRequest getRequest(String type) {
        HealeniumHttpRequest service = healeniumHttpRequestsCache.get(type);
        if(service == null) throw new RuntimeException("Unknown request type: " + type);
        return service;
    }

}
