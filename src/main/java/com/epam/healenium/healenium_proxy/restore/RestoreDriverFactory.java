package com.epam.healenium.healenium_proxy.restore;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.remote.CapabilityType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j(topic = "healenium")
@Service
public class RestoreDriverFactory {

    private final RestoreDriver webRestoreDriverServices;
    private final RestoreDriver appNativeRestoreDriverServices;

    public RestoreDriverFactory(@Qualifier("restoreWebDriver") RestoreDriver webRestoreDriverServices,
                                @Qualifier("restoreMobileNativeDriver") RestoreDriver appNativeRestoreDriverServices) {
        this.webRestoreDriverServices = webRestoreDriverServices;
        this.appNativeRestoreDriverServices = appNativeRestoreDriverServices;
    }

    public RestoreDriver getRestoreService(Map<String, Object> capabilities) {
        return capabilities.containsKey(CapabilityType.BROWSER_NAME)
                && !StringUtils.isEmpty((String) capabilities.get(CapabilityType.BROWSER_NAME))
                ? webRestoreDriverServices
                : appNativeRestoreDriverServices;
    }
}
