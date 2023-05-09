package org.av360.maverick.graph.feature.jobs.schedulers;

import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.feature.jobs.ExportApplicationJob;
import org.av360.maverick.graph.model.events.JobScheduledEvent;
import org.av360.maverick.graph.model.security.AdminToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j(topic = "graph.jobs.exports")
@ConditionalOnProperty(name = "application.features.modules.jobs.scheduled.exportApplication", havingValue = "true")
public class ScheduledExportApplication {
    private final ApplicationEventPublisher eventPublisher;

    public ScheduledExportApplication(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }



    @Scheduled(initialDelay = 60, fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    public void scheduled() {
        JobScheduledEvent event = new JobScheduledEvent(ExportApplicationJob.NAME, new AdminToken());
        eventPublisher.publishEvent(event);
    }
}
