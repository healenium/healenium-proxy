package com.epam.healenium.healenium_proxy.model.elitea;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class EliteaDto {
    private List<EliteaHealing> healings;
    private String repoName;
}
