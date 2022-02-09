package com.epam.healenium.healenium_proxy.service;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Platform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RestoreDriverServiceFactory {

    @Autowired
    private List<RestoreDriverService> restoreDriverServices;

    private static final Map<Platform, RestoreDriverService> restoreDriverServicesCache = new HashMap<>();

    @PostConstruct
    public void initRequestCache() {
        for (RestoreDriverService service : restoreDriverServices) {
            restoreDriverServicesCache.put(service.getPlatformName(), service);
        }
    }

    public static RestoreDriverService getRestoreService(String platformName) {
        RestoreDriverService service = restoreDriverServicesCache.get(Platform.fromString(platformName));
        if (service == null) {
            service = restoreDriverServicesCache.get(Platform.LINUX);
        }
        return service;
    }
}
