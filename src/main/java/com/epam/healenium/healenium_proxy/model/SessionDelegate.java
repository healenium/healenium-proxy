package com.epam.healenium.healenium_proxy.model;

import lombok.Data;

import java.util.HashMap;

@Data
public class SessionDelegate {

    private HashMap<String, Object> capabilities;
    private String url;

}
