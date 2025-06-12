package com.epam.healenium.healenium_proxy.model.elitea;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class RunEliteaAgentRequest {

    private List<String> chat_history;
    private String user_input;

    public RunEliteaAgentRequest(List<String> chat_history, String user_input) {
        this.chat_history = chat_history;
        this.user_input = user_input;
    }

}
