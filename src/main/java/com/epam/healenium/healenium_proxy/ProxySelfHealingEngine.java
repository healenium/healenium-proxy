//package com.epam.healenium.healenium_proxy;
//
//import com.epam.healenium.SelfHealingEngine;
//import com.epam.healenium.healenium_proxy.rest.HealeniumRestService;
//import com.epam.healenium.model.Context;
//import com.typesafe.config.Config;
//import lombok.AllArgsConstructor;
//import org.jetbrains.annotations.NotNull;
//import org.openqa.selenium.WebDriver;
//import org.openqa.selenium.WebElement;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//public class ProxySelfHealingEngine extends SelfHealingEngine {
//
//    private final HealeniumRestService healeniumRestService;
//
//    public ProxySelfHealingEngine(@NotNull WebDriver delegate, @NotNull Config config) {
//        super(delegate, config);
//    }
//
//    public ProxySelfHealingEngine(@NotNull WebDriver delegate) {
//        super(delegate);
//    }
//
//    @Override
//    public void saveElements(Context context, List<WebElement> webElements) {
//        healeniumRestService.saveElements(context.getElementIds());
//        super.saveElements(context, webElements);
//    }
//}
