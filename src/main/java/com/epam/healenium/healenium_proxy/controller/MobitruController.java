package com.epam.healenium.healenium_proxy.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * WIP Mobitru candidate endpoint — stubbed so the proxy stays buildable.
 * Restore real healing wiring when Mobitru integration is ready.
 */
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/proxy")
public class MobitruController {

    @PostMapping("/candidate")
    public ResponseEntity<Void> getCandidate(@RequestBody Map<String, Object> body) {
        log.info("[Mobitru] /proxy/candidate not implemented yet; body={}", body);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
