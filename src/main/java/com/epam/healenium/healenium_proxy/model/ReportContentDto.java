package com.epam.healenium.healenium_proxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class ReportContentDto {
    private String id;
    private String name;
    private String time;
    private List<ReportRecord> data = new ArrayList<>();
    private boolean detected;

    @Data
    public static class ReportRecord {
        private String declaringClass;
        private String screenShotPath;
        private String failedLocatorValue;
        private String failedLocatorType;
        private String healedLocatorValue;
        private String healedLocatorType;
        private String score;
        private boolean successHealing;
        private Integer healingResultId;
    }
}
