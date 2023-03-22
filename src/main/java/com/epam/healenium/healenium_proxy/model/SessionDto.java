package com.epam.healenium.healenium_proxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.net.URL;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class SessionDto {

    private URL addressOfRemoteServer;
    private String sessionId;
    private Map<String, Object> sessionCapabilities;

}
