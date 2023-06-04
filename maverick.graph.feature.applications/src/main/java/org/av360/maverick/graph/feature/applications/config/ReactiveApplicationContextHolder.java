package org.av360.maverick.graph.feature.applications.config;

import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.feature.applications.domain.model.Application;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.concurrent.ExecutionException;

@Slf4j(topic = "graph.feat.app.context")
public class ReactiveApplicationContextHolder {

    public static final Class<Application> CONTEXT_KEY = Application.class;

    public static Mono<Application> getRequestedApplication() {
        return Mono.deferContextual(Mono::just)
                .filter(ctx -> ctx.hasKey(CONTEXT_KEY))
                .map(ctx -> ctx.get(CONTEXT_KEY))
                .switchIfEmpty(Mono.empty())
                .doOnError(error -> log.error("Failed to read node from context due to error: {}", error.getMessage()));

    }

    public static Application getRequestedApplicationBlocking() {
        try {

            Application block = getRequestedApplication().block();
            Application application = getRequestedApplication()
                    .map(app -> {
                        return app;
                    })
                    .toFuture().get();
            return application;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

    }

    public static Context withApplication(Application requestedApplication) {
        return Context.of(CONTEXT_KEY, requestedApplication);
    }
}
