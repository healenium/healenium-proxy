package com.epam.healenium.healenium_proxy.model.elitea;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ChatHistory {
    private String content;
    private String role;
}
