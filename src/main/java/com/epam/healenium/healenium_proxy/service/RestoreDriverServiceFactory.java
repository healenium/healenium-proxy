package com.epam.healenium.healenium_proxy.service;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class RestoreDriverServiceFactory {

    private final RestoreDriverService webRestoreDriverServices;
    private final RestoreDriverService appNativeRestoreDriverServices;

    public RestoreDriverServiceFactory(@Qualifier("restoreWebDriverServiceImpl") RestoreDriverService webRestoreDriverServices,
                                       @Qualifier("restoreMobileNativeDriverServiceImpl") RestoreDriverService appNativeRestoreDriverServices) {
        this.webRestoreDriverServices = webRestoreDriverServices;
        this.appNativeRestoreDriverServices = appNativeRestoreDriverServices;
    }

    public RestoreDriverService getRestoreService(Map<String, Object> capabilities) {
        return capabilities.containsKey(CapabilityType.BROWSER_NAME)
                ? webRestoreDriverServices
                : appNativeRestoreDriverServices;
    }
}
