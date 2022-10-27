package com.epam.healenium.healenium_proxy.request.post.override;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@Service
public class HealeniumHttpPostRequestFactory {

    private final List<HealeniumHttpPostRequest> postRequests;

    private static final Map<String, HealeniumHttpPostRequest> postRequestsCache = new HashMap<>();

    @PostConstruct
    public void initRequestCache() {
        postRequests.forEach(service -> postRequestsCache.put(service.getURL(), service));
    }

    public HealeniumHttpPostRequest getRequest(String type) {
        return postRequestsCache.entrySet().stream()
                .filter(item -> type.endsWith(item.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
