package io.av360.maverick.graph.api.security.ext;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j(topic = "graph.config.security.default")
public class ChainingAuthenticationManager implements ReactiveAuthenticationManager {

    private final List<ReactiveAuthenticationManager> delegates;

    public ChainingAuthenticationManager(ReactiveAuthenticationManager... entryPoints) {
        this(Arrays.asList(entryPoints));
    }

    public ChainingAuthenticationManager(List<ReactiveAuthenticationManager> entryPoints) {
        Assert.notEmpty(entryPoints, "entryPoints cannot be null");
        this.delegates = entryPoints;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {

        AtomicReference<Authentication> current = new AtomicReference<>(authentication);
        // @formatter:off
        return Flux.fromIterable(this.delegates)
                // see example here: https://stackoverflow.com/questions/73141978/how-to-asynchronosuly-reduce-a-flux-to-mono-with-reactor
                .reduceWith(() -> Mono.just(authentication), (update, reactiveAuthenticationManager) -> update.flatMap(reactiveAuthenticationManager::authenticate))
                .flatMap(authenticationMono -> authenticationMono);


    }
}
