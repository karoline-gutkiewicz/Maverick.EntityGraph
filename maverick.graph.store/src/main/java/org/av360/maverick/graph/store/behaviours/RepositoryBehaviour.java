package org.av360.maverick.graph.store.behaviours;


import org.av360.maverick.graph.model.security.Authorities;
import org.av360.maverick.graph.store.RepositoryBuilder;
import org.av360.maverick.graph.store.RepositoryType;
import org.av360.maverick.graph.store.rdf.fragments.RdfTransaction;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryLockedException;
import org.eclipse.rdf4j.repository.base.RepositoryConnectionWrapper;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

public interface RepositoryBehaviour {


    default ValueFactory getValueFactory() {
        return SimpleValueFactory.getInstance();
    }


    /**
     * Checks whether an entity with the given identity exists, ie. we have an crdf:type statement.
     *
     * @param subj the id of the entity
     * @return true if exists
     */
    default Mono<Boolean> exists(Resource subj, Authentication authentication) {
        return this.exists(subj, authentication, Authorities.READER);
    }

    Mono<Boolean> exists(Resource subj, Authentication authentication, GrantedAuthority requiredAuthority);


    Flux<IRI> types(Resource subj, Authentication authentication, GrantedAuthority requiredAuthority);

    Flux<RdfTransaction> commit(Collection<RdfTransaction> transactions, Authentication authentication, GrantedAuthority requiredAuthority);

    default Mono<RdfTransaction> commit(RdfTransaction transaction, Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.commit(List.of(transaction), authentication, requiredAuthority).singleOrEmpty();
    }


    default Mono<RepositoryConnection> getConnection(Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.getConnection(authentication, getRepositoryType(), requiredAuthority);
    }

    default Mono<RepositoryConnection> getConnection(Authentication authentication, RepositoryType repositoryType, GrantedAuthority requiredAuthority)  {
        if (!Authorities.satisfies(requiredAuthority, authentication.getAuthorities())) {
            String msg = String.format("Required authority '%s' for repository '%s' not met in authentication with authorities '%s'", requiredAuthority.getAuthority(), repositoryType.name(), authentication.getAuthorities());
            return Mono.error(new InsufficientAuthenticationException(msg));
        }
        return getBuilder().buildRepository(repositoryType, authentication)
                .map(repository -> new RepositoryConnectionWrapper(repository, repository.getConnection()))
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1)).filter(throwable -> throwable instanceof RepositoryLockedException))
                .map(repositoryConnectionWrapper -> repositoryConnectionWrapper);
    }

    RepositoryType getRepositoryType();

    RepositoryBuilder getBuilder();

}
