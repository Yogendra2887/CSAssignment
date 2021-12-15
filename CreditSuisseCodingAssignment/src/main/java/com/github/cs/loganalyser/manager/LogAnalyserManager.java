
package com.github.cs.loganalyser.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cs.loganalyser.conf.ApplicationData;
import com.github.cs.loganalyser.model.Context;
import com.github.cs.loganalyser.model.Event;
import com.github.cs.loganalyser.model.State;
import com.github.cs.loganalyser.model.persistence.Alert;
import com.github.cs.loganalyser.repository.AlertRepository;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Stream;

@Component
public class LogAnalyserManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogAnalyserManager.class);

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private ApplicationData applicationData;

    public void parseAndPersistEvents(Context context)  {
        // EventMap temporarily holds the events while we find the matching-STARTED or FINISHED-events.
        // Once found, the corresponding event would be removed from the map.
        Map<String, Event> eventMap = new HashMap<>();

        //Alerts map holds the events that are parsed before persisting in a DB table.
        // Each alert would have its execution time calculated and flagged (isAlert TRUE or FALSE).
        Map<String, Alert> alerts = new HashMap<>();

        LOGGER.info("Parsing the events and persisting the alerts. This may take a while...");
        try {
        FileUtils.write(new ClassPathResource("output.json").getFile(), "", Charset.defaultCharset());
        LineIterator li = FileUtils.lineIterator(new ClassPathResource("samples/" + context.getLogFilePath()).getFile());
            String line = null;
            while (li.hasNext()) {
                Event event;
                try {
                    event = new ObjectMapper().readValue(li.nextLine(), Event.class);
                    LOGGER.trace("{}", event);

                    // Check if we have either STARTED or FINISHED event already for the given ID.
                    // If yes, then find the execution time between STARTED and FINISHED states and update the alert.
                    if (eventMap.containsKey(event.getId())) {
                        Event e1 = eventMap.get(event.getId());
                        long executionTime = getEventExecutionTime(event, e1);

                        // the alert created off an event would have the alert flag set to FALSE by default.
                        Alert alert = new Alert(event, Math.toIntExact(executionTime));

                        // if the execution time is more than the specified threshold, flag the alert as TRUE
                        if (executionTime > applicationData.getEventDurationThreshold()) {
                            alert.setAlert(Boolean.TRUE);
                            LOGGER.trace("!!! Execution time for the event {} is {}ms", event.getId(), executionTime);
                        }

                        // add it to the pool of alerts that are yet to be persisted
                        alerts.put(event.getId(), alert);

                        // remove from the temporary map as we found the matching event
                        eventMap.remove(event.getId());
                    } else {
                        eventMap.put(event.getId(), event);
                    }
                } catch (JsonProcessingException e) {
                    LOGGER.error("Unable to parse the event! {}", e.getMessage());
                }

                // to reduce memory consumption, write off the alerts once the pool has enough alerts
                if (alerts.size() > applicationData.getTableRowsBatchCount()) {
                    persistAlerts(alerts.values());
                    writeAlertsToFile(alerts.values());
                    alerts = new HashMap<>();
                }
            } // END while
            if (alerts.size() != 0) {
                persistAlerts(alerts.values());
                writeAlertsToFile(alerts.values());
            }
        } catch (IOException e) {
            LOGGER.error("!!! Unable to access the file: {}", e.getMessage());
        }
    }
    private void writeAlertsToFile(Collection<Alert> alerts) {
        LOGGER.debug("writing to output file {} alerts...", alerts.size());
        ObjectMapper mapper = new ObjectMapper();
        try {
            File file = new ClassPathResource("output.json").getFile();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(alerts);
            Files.write(file.toPath(), Arrays.asList(json), StandardOpenOption.APPEND);
        } catch (JsonProcessingException e) {
            LOGGER.error("Unable to parse and write the alerts in file! {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("!!! Unable to write alerts in the output file: {}", e.getMessage());
        }
    }


    private void persistAlerts(Collection<Alert> alerts) {
        LOGGER.debug("Persisting {} alerts...", alerts.size());
        alertRepository.saveAll(alerts);
    }

    private long getEventExecutionTime(Event event1, Event event2) {
        Event endEvent = Stream.of(event1, event2).filter(e -> State.FINISHED.equals(e.getState())).findFirst().orElse(null);
        Event startEvent = Stream.of(event1, event2).filter(e -> State.STARTED.equals(e.getState())).findFirst().orElse(null);

        return Objects.requireNonNull(endEvent).getTimestamp() - Objects.requireNonNull(startEvent).getTimestamp();
    }
}
