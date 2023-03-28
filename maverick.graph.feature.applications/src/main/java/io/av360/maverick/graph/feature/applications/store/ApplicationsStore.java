package io.av360.maverick.graph.feature.applications.store;

import io.av360.maverick.graph.store.RepositoryType;
import io.av360.maverick.graph.store.behaviours.ModelUpdates;
import io.av360.maverick.graph.store.behaviours.Resettable;
import io.av360.maverick.graph.store.behaviours.Searchable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import reactor.core.publisher.Mono;

public interface ApplicationsStore extends Searchable, ModelUpdates, Resettable {

    default Mono<Void> reset(Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.reset(authentication, RepositoryType.APPLICATION, requiredAuthority);
    }
}
