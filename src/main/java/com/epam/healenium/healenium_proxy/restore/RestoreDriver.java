package com.epam.healenium.healenium_proxy.restore;

import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.typesafe.config.Config;

public interface RestoreDriver {

    void restoreSelfHealing(String sessionId, ProxySessionContext proxySessionContext, Config config);

}
