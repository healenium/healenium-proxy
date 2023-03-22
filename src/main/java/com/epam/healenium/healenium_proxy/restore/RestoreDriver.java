package com.epam.healenium.healenium_proxy.restore;

import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.typesafe.config.Config;

public interface RestoreDriver {

    void restoreSelfHealing(String sessionId, SessionContext sessionContext, Config config);

}
