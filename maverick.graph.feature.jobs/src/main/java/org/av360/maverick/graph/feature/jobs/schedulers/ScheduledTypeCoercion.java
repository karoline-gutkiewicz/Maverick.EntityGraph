package org.av360.maverick.graph.feature.jobs.schedulers;

import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.feature.jobs.AssignInternalTypesJob;
import org.av360.maverick.graph.model.context.SessionContext;
import org.av360.maverick.graph.model.events.JobScheduledEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * If we have any global identifiers (externally set) in the repo, we have to replace them with our internal identifiers.
 * Otherwise we cannot address the entities through our API.
 * <p>
 * Periodically runs the following sparql queries, grabs the entity definition for it and regenerates the identifiers
 * <p>
 *     <pre>
 * SELECT ?a WHERE { ?a a ?c . }
 * FILTER NOT EXISTS {
 * FILTER STRSTARTS(str(?a), "http //graphs.azurewebsites.net/api/entities/").
 * }
 * LIMIT 100
 * </pre>
 */
@Slf4j(topic = "graph.jobs.identifiers")
@Component
@ConditionalOnProperty(name = "application.features.modules.jobs.scheduled.typeCoercion.enabled", havingValue = "true")
public class ScheduledTypeCoercion  {

    private final ApplicationEventPublisher eventPublisher;
    public ScheduledTypeCoercion(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

//    @Scheduled(initialDelay = 30, fixedRate = 600, timeUnit = TimeUnit.SECONDS)
    @Scheduled(cron = "${application.features.modules.jobs.scheduled.typeCoercion.defaultFrequency:0 */5 * * * ?}")
    public void scheduled() {
        JobScheduledEvent event = new JobScheduledEvent(AssignInternalTypesJob.NAME, new SessionContext().setSystemAuthentication());
        eventPublisher.publishEvent(event);
    }

}