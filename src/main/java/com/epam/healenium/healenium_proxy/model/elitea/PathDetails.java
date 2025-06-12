package com.epam.healenium.healenium_proxy.model.elitea;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PathDetails {
    private String path;
    private String reason;
    private String codeSnippet;
}
