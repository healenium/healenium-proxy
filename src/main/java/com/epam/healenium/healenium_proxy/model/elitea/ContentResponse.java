package com.epam.healenium.healenium_proxy.model.elitea;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ContentResponse {
    private String status;
    private String error_message;
    private String url;
    private String title;
    private String number;
}
