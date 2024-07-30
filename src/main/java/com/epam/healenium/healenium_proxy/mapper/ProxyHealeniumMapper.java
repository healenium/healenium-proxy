package com.epam.healenium.healenium_proxy.mapper;

import com.epam.healenium.mapper.HealeniumMapper;
import com.epam.healenium.model.RequestDto;
import com.epam.healenium.utils.StackTraceReader;
import org.openqa.selenium.By;

public class ProxyHealeniumMapper extends HealeniumMapper {

    public ProxyHealeniumMapper(StackTraceReader stackTraceReader) {
        super(stackTraceReader);
    }

    @Override
    public RequestDto buildDto(By by, String command, String currentUrl) {
        String[] locatorParts = by.toString().split(":", 2);
        RequestDto dto = new RequestDto()
                .setLocator(locatorParts[1].trim())
                .setType(locatorParts[0].trim());
        dto.setClassName("HealeniumFindElementPostRequest");
        dto.setMethodName("findElement");
        dto.setCommand(command);
        dto.setUrl(currentUrl);
        return dto;
    }
}
