package com.epam.healenium.healenium_proxy.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OriginalResponse {
    private String body;
    private int status;
}
