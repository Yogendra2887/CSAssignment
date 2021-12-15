package com.github.cs.loganalyser.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.log-analyser")
public class ApplicationData {
    private int eventDurationThreshold;
    private int tableRowsBatchCount;

    public int getEventDurationThreshold() {
        return eventDurationThreshold;
    }

    public void setEventDurationThreshold(int eventDurationThreshold) {
        this.eventDurationThreshold = eventDurationThreshold;
    }

    public int getTableRowsBatchCount() {
        return tableRowsBatchCount;
    }

    public void setTableRowsBatchCount(int tableRowsBatchCount) {
        this.tableRowsBatchCount = tableRowsBatchCount;
    }
}
