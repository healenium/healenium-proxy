package com.epam.healenium.healenium_proxy.model.elitea;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ValidPath {
    private String path;
    private String codeSnippet;
}
