package com.epam.healenium.healenium_proxy.controller;


import com.epam.healenium.healenium_proxy.model.ProxySessionContext;
import com.epam.healenium.healenium_proxy.service.SessionContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j(topic = "healenium")
@RequiredArgsConstructor
@RestController
@RequestMapping("/session/{sessionId}/healenium")
public class SessionController {

    private final SessionContextService sessionContextService;

    @PostMapping("/params")
    public ResponseEntity<Void> updateSession(@PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        log.info("[Params] {}", body);
        Boolean isWaitCommand = (Boolean) body.get("isWait");
        ProxySessionContext proxySessionContext = sessionContextService.getSessionContext(sessionId);
        if (proxySessionContext != null) {
            proxySessionContext.getSelfHealingEngine().getSessionContext().setWaitCommand(isWaitCommand);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
