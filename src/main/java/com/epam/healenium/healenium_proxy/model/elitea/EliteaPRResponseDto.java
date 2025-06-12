package com.epam.healenium.healenium_proxy.model.elitea;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class EliteaPRResponseDto {
    private List<ChatHistory> chat_history;
}
