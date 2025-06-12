package com.epam.healenium.healenium_proxy.model.elitea;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class EliteaSelectorDetectionResponseDto {
    private String id;
    private String locator;
    private String locatorType;
    private List<PathDetails> validPaths;
    private List<PathDetails> invalidPaths;
}
