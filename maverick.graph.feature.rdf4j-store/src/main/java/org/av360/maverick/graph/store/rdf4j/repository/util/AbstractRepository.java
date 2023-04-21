package org.av360.maverick.graph.store.rdf4j.repository.util;

import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.model.enums.Activity;
import org.av360.maverick.graph.model.rdf.AnnotatedStatement;
import org.av360.maverick.graph.model.security.Authorities;
import org.av360.maverick.graph.model.vocabulary.Transactions;
import org.av360.maverick.graph.store.RepositoryBuilder;
import org.av360.maverick.graph.store.RepositoryType;
import org.av360.maverick.graph.store.behaviours.ModelUpdates;
import org.av360.maverick.graph.store.behaviours.RepositoryBehaviour;
import org.av360.maverick.graph.store.behaviours.Resettable;
import org.av360.maverick.graph.store.behaviours.Statements;
import org.av360.maverick.graph.store.rdf.fragments.RdfTransaction;
import org.av360.maverick.graph.store.rdf.helpers.RdfUtils;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleNamespace;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryLockedException;
import org.eclipse.rdf4j.repository.base.RepositoryConnectionWrapper;
import org.eclipse.rdf4j.repository.util.RDFInserter;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFParserFactory;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j(topic = "graph.repo.base")
public abstract class AbstractRepository implements RepositoryBehaviour, Statements, ModelUpdates, Resettable {

    private final RepositoryType repositoryType;
    private RepositoryBuilder repositoryConfiguration;

    public AbstractRepository(RepositoryType repositoryType) {
        this.repositoryType = repositoryType;
    }


    public RepositoryType getRepositoryType() {
        return this.repositoryType;
    }

    @Override
    public RepositoryBuilder getBuilder() {
        return this.repositoryConfiguration;
    }

    @Autowired
    private void setConfiguration(RepositoryBuilder repositoryConfiguration) {
        this.repositoryConfiguration = repositoryConfiguration;
    }


    public Flux<AnnotatedStatement> construct(String query, Authentication authentication, GrantedAuthority requiredAuthority, RepositoryType repositoryType) {
        return this.executeMany(authentication, requiredAuthority, repositoryType, connection -> {
            try {
                log.trace("Running construct query in repository: {}", connection.getRepository());

                GraphQuery q = connection.prepareGraphQuery(QueryLanguage.SPARQL, query);
                try (GraphQueryResult result = q.evaluate()) {
                    Set<Namespace> namespaces = result.getNamespaces().entrySet().stream()
                            .map(entry -> new SimpleNamespace(entry.getKey(), entry.getValue()))
                            .collect(Collectors.toSet());
                    return result.stream().map(statement -> AnnotatedStatement.wrap(statement, namespaces)).collect(Collectors.toSet());
                } catch (Exception e) {
                    log.warn("Error while running value query.", e);
                    throw e;
                }
            } catch (MalformedQueryException e) {
                log.warn("Error while parsing query", e);
                throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid query");
            } catch (Exception e) {
                log.error("Unknown error while running query", e);
                throw e;
            }
        });

    }

    public Flux<BindingSet> query(String query, Authentication authentication, GrantedAuthority requiredAuthority, RepositoryType repositoryType) {
        return this.executeMany(authentication, requiredAuthority, repositoryType, connection -> {
            try {

                TupleQuery q = connection.prepareTupleQuery(QueryLanguage.SPARQL, query);
                if (log.isTraceEnabled())
                    log.trace("Querying repository '{}'", connection.getRepository());
                try (TupleQueryResult result = q.evaluate()) {
                    return result.stream().collect(Collectors.toSet());
                }

            } catch (MalformedQueryException e) {
                log.warn("Error while parsing query, reason: {}", e.getMessage());
                throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid query");
            } catch (Exception e) {
                log.error("Unknown error while running query", e);
                throw e;
            }

        });

    }

    @Override
    public Mono<Void> reset(Authentication authentication, RepositoryType repositoryType, GrantedAuthority requiredAuthority) {
        return this.execute(authentication, requiredAuthority, connection -> {
            try {
                if (!connection.isOpen() || connection.isActive()) return null;

                if (log.isTraceEnabled()) {
                    // RepositoryResult<Statement> statements = connection.getStatements(null, null, null);
                    log.trace("Removing {} statements from repository '{}'", connection.size(), connection.getRepository());
                }

                connection.clear();

                if (!connection.isEmpty())
                    throw new RepositoryException("Repository not empty after clearing");

                return null;
            } catch (Exception e) {
                log.error("Failed to clear repository: {}", connection.getRepository());
                throw e;
            }
        });
    }


    @Override
    public Mono<Void> delete(Model model, Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.execute(authentication, requiredAuthority, connection -> {
            try {
                Resource[] contexts = model.contexts().toArray(new Resource[0]);
                connection.add(model, contexts);
                connection.commit();
                return null;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        });
    }


    InputStream getInputStreamFromFluxDataBuffer(Publisher<DataBuffer> data) throws IOException {
        PipedOutputStream osPipe = new PipedOutputStream();
        PipedInputStream isPipe = new PipedInputStream(osPipe);

        DataBufferUtils.write(data, osPipe)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnComplete(() -> {
                    try {
                        log.trace("Finished reading data buffers from publisher during import of data.");
                        osPipe.close();
                    } catch (IOException ignored) {
                    }
                })
                .doOnSubscribe(subscription -> log.trace("Starting reading data buffers from publisher during import of data."))
                .subscribe(DataBufferUtils.releaseConsumer());
        return isPipe;
    }

    @Override
    public Mono<Void> importStatements(Publisher<DataBuffer> bytesPublisher, String mimetype, Authentication authentication, GrantedAuthority requiredAuthority) {

        Optional<RDFParserFactory> parserFactory = RdfUtils.getParserFactory(MimeType.valueOf(mimetype));
        Assert.isTrue(parserFactory.isPresent(), "Unsupported mimetype for parsing the file.");

        RDFParser parser = parserFactory.orElseThrow().getParser();

        return this.execute(authentication, requiredAuthority, connection -> {
            try {
                // example: https://www.baeldung.com/spring-reactive-read-flux-into-inputstream
                // solution: https://manhtai.github.io/posts/flux-databuffer-to-inputstream/
                log.trace("Starting to parse input stream with mimetype {}", mimetype);

                RDFInserter rdfInserter = new RDFInserter(connection);
                parser.setRDFHandler(rdfInserter);

                InputStream stream = getInputStreamFromFluxDataBuffer(bytesPublisher);
                parser.parse(stream);

                log.trace("Stored imported rdf into repository '{}'", connection.getRepository().toString());
                return null;

            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } catch (Exception exception) {
                log.error("Failed to import statements with mimetype {} with reason: ", mimetype, exception);
                throw exception;
            }
        });

    }

    public Flux<IRI> types(Resource subj, Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.executeMany(authentication, requiredAuthority, connection ->
                connection.getStatements(subj, RDF.TYPE, null, false).stream()
                        .map(Statement::getObject)
                        .filter(Value::isIRI)
                        .map(value -> (IRI) value)
                        .collect(Collectors.toSet()));
    }


    @Override
    public Flux<RdfTransaction> commit(Collection<RdfTransaction> transactions, Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.executeMany(authentication, requiredAuthority, connection ->
                transactions.stream().peek(trx -> {
                    log.trace("Committing transaction '{}' to repository '{}'", trx.getIdentifier().getLocalName(), connection.getRepository().toString());

                    // FIXME: the approach based on the context works only as long as the statements in the graph are all within the global context only
                    // with this approach, we cannot insert a statement to a context (since it is already in GRAPH_CREATED), every st can only be in one context
                    Model insertModel = trx.getModel().filter(null, null, null, Transactions.GRAPH_CREATED);
                    Model removeModel = trx.getModel().filter(null, null, null, Transactions.GRAPH_DELETED);

                    ValueFactory vf = connection.getValueFactory();
                    List<Statement> insertStatements = insertModel.stream().map(s -> vf.createStatement(s.getSubject(), s.getPredicate(), s.getObject())).toList();
                    List<Statement> removeStatements = removeModel.stream().map(s -> vf.createStatement(s.getSubject(), s.getPredicate(), s.getObject())).toList();

                    try {
                        connection.begin();
                        connection.add(insertStatements);
                        connection.remove(removeStatements);
                        connection.commit();

                        trx.setCompleted();
                        log.trace("Transaction '{}' completed with {} inserted statements and {} removed statements in repository '{}'.", trx.getIdentifier().getLocalName(), insertStatements.size(), removeStatements.size(), connection.getRepository());
                    } catch (Exception e) {
                        log.error("Failed to complete transaction for repository '{}'.", connection.getRepository(), e);
                        log.trace("Insert Statements in this transaction: \n {}", insertModel);
                        log.trace("Remove Statements in this transaction: \n {}", removeModel);
                        connection.rollback();
                        trx.setFailed(e.getMessage());
                    }
                }).collect(Collectors.toSet()));
    }


    @Override
    public Mono<Void> insert(Model model, Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.execute(authentication, requiredAuthority, connection -> {
            try {
                if (log.isTraceEnabled())
                    log.trace("Inserting model without transaction to repository '{}'", connection.getRepository().toString());

                Resource[] contexts = model.contexts().toArray(new Resource[0]);
                connection.add(model, contexts);
                connection.commit();
                return null;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        });

    }


    @Override
    public Mono<Set<Statement>> listStatements(Resource value, IRI predicate, Value object, Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.execute(authentication, requiredAuthority, connection -> {
            if (log.isTraceEnabled()) {
                log.trace("Listing all statements with pattern [{},{},{}] from repository '{}'", value, predicate, object, connection.getRepository().toString());
            }

            Set<Statement> statements = connection.getStatements(value, predicate, object).stream().collect(Collectors.toUnmodifiableSet());
            return statements;
        });

    }

    @Override
    public Mono<Boolean> hasStatement(Resource value, IRI predicate, Value object, Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.execute(authentication, requiredAuthority, connection -> connection.hasStatement(value, predicate, object, false));

    }


    @Override
    public Mono<Boolean> exists(Resource subj, Authentication authentication, GrantedAuthority requiredAuthority) {
        return this.execute(authentication, requiredAuthority, connection -> connection.hasStatement(subj, RDF.TYPE, null, false));
    }

    @Override
    public Mono<RdfTransaction> insert(Model model, RdfTransaction transaction) {
        Assert.notNull(transaction, "Transaction cannot be null");
        if (log.isTraceEnabled())
            log.trace("Insert planned for {} statements in transaction '{}'.", model.size(), transaction.getIdentifier().getLocalName());


        transaction = transaction
                .insert(model, Activity.INSERTED)
                .affected(model);

        return transaction.asMono();
    }


    @Override
    public Mono<RdfTransaction> removeStatements(Collection<Statement> statements, RdfTransaction transaction) {
        Assert.notNull(transaction, "Transaction cannot be null");
        if (log.isTraceEnabled())
            log.trace("Removal planned for {} statements in transaction '{}'.", statements.size(), transaction.getIdentifier().getLocalName());

        return transaction
                .remove(statements, Activity.REMOVED)
                .asMono();
    }

    @Override
    public Mono<RdfTransaction> addStatement(Resource subject, IRI predicate, Value literal, RdfTransaction transaction) {
        Assert.notNull(transaction, "Transaction cannot be null");
        if (log.isTraceEnabled())
            log.trace("Marking statement for insert in transaction {}: {} - {} - {}", transaction.getIdentifier().getLocalName(), subject, predicate, literal);

        return transaction
                .insert(subject, predicate, literal, Activity.UPDATED)
                .affected(subject, predicate, literal)
                .asMono();

    }

    public Mono<RepositoryConnection> getConnection(Authentication authentication, RepositoryType repositoryType, GrantedAuthority requiredAuthority) {
        if (!Authorities.satisfies(requiredAuthority, authentication.getAuthorities())) {
            String msg = String.format("Required authority '%s' for repository '%s' not met in authentication with authorities '%s'", requiredAuthority.getAuthority(), repositoryType.name(), authentication.getAuthorities());
            return Mono.error(new InsufficientAuthenticationException(msg));
        }
        return getBuilder().buildRepository(repositoryType, authentication)
                .map(repository -> new RepositoryConnectionWrapper(repository, repository.getConnection()))
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(1)).filter(throwable -> throwable instanceof RepositoryLockedException))
                .map(repositoryConnectionWrapper -> repositoryConnectionWrapper);
    }


    protected <T> Mono<T> execute(Authentication authentication, GrantedAuthority requiredAuthority, RepositoryType repositoryType, Function<RepositoryConnection, T> fun) {
        if (!Authorities.satisfies(requiredAuthority, authentication.getAuthorities())) {
            String msg = String.format("Required authority '%s' for repository '%s' not met in authentication with authorities '%s'", requiredAuthority.getAuthority(), repositoryType.name(), authentication.getAuthorities());
            return Mono.error(new InsufficientAuthenticationException(msg));
        }
        return getBuilder().buildRepository(repositoryType, authentication)
                .flatMap(repository -> {
                    try (RepositoryConnection connection = repository.getConnection()) {

                        T result = fun.apply(new RepositoryConnectionWrapper(repository, connection));
                        if(Objects.isNull(result)) return Mono.empty();
                        else return Mono.just(result);
                    } catch (Exception e) {
                        return Mono.error(e);
                    } finally {
                        log.trace("Applied mono function to repository '{}'", repository);
                    }
                });
    }

    protected <T> Mono<T> execute(Authentication authentication, GrantedAuthority requiredAuthority, Function<RepositoryConnection, T> fun) {
        return this.execute(authentication, requiredAuthority, this.getRepositoryType(), fun);
    }

    protected <E, T extends Iterable<E>> Flux<E> executeMany(Authentication authentication, GrantedAuthority requiredAuthority, RepositoryType repositoryType, Function<RepositoryConnection, T> fun) {
        if (!Authorities.satisfies(requiredAuthority, authentication.getAuthorities())) {
            String msg = String.format("Required authority '%s' for repository '%s' not met in authentication with authorities '%s'", requiredAuthority.getAuthority(), repositoryType.name(), authentication.getAuthorities());
            return Flux.error(new InsufficientAuthenticationException(msg));
        }
        return getBuilder().buildRepository(repositoryType, authentication)
                .flatMapMany(repository -> {
                    try (RepositoryConnection connection = repository.getConnection()) {
                        T result = fun.apply(connection);
                        return Flux.fromIterable(result);
                    } catch (Exception e) {
                        return Flux.error(e);
                    } finally {
                        log.trace("Applied flux function to repository '{}': {}", repository, fun.toString());
                    }
                });
    }


    protected <E, T extends Iterable<E>> Flux<E> executeMany(Authentication authentication, GrantedAuthority requiredAuthority, Function<RepositoryConnection, T> fun) {
        return this.executeMany(authentication, requiredAuthority, this.getRepositoryType(), fun);
    }
}