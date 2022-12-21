package com.epam.healenium.healenium_proxy.restore;

import com.epam.healenium.handlers.SelfHealingHandler;
import com.epam.healenium.healenium_proxy.model.SessionContext;
import com.typesafe.config.Config;

public interface RestoreDriver {

    SelfHealingHandler restoreSelfHealingHandlerDrive(String currentSessionId, SessionContext sessionContext, Config config);

    SelfHealingHandler restoreSelfHealingHandlerWebElement(String currentSessionId, SessionContext sessionContext, Config config);
}
