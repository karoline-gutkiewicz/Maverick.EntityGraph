package org.av360.maverick.graph.api.controller.queries;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.api.controller.AbstractController;
import org.av360.maverick.graph.model.enums.RepositoryType;
import org.av360.maverick.graph.model.enums.SparqlMimeTypes;
import org.av360.maverick.graph.model.rdf.AnnotatedStatement;
import org.av360.maverick.graph.services.QueryServices;
import org.eclipse.rdf4j.query.BindingSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping(path = "/api/query")
@Slf4j(topic = "graph.ctrl.queries")
@SecurityRequirement(name = "api_key")
public class QueryRestController extends AbstractController {
    protected final QueryServices queryServices;

    public QueryRestController(QueryServices queryServices) {
        this.queryServices = queryServices;
    }

    @PostMapping(value = "/select", consumes = {MediaType.TEXT_PLAIN_VALUE, SparqlMimeTypes.SPARQL_QUERY_VALUE}, produces = {SparqlMimeTypes.CSV_VALUE, SparqlMimeTypes.JSON_VALUE})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Sparql Select Query",
            content = @Content(examples = {
                    @ExampleObject(name = "Select types", value = "SELECT ?entity  ?type WHERE { ?entity a ?type } LIMIT 100"),
                    @ExampleObject(name = "Query everything", value = "SELECT ?a ?b ?c  ?type WHERE { ?a ?b ?c } LIMIT 100")
            })
    )
    @ResponseStatus(HttpStatus.OK)
    public Flux<BindingSet> queryBindingsPost(@RequestBody String query,
                                          @RequestParam(required = false, defaultValue = "entities", value = "entities") @Parameter(name = "repository", description = "The repository type in which the query should search.")
                                          RepositoryType repositoryType) {

        return super.acquireContext()
                .flatMapMany(ctx -> queryServices.queryValues(query, repositoryType, ctx))
                .doOnSubscribe(s -> {
                    if (log.isDebugEnabled()) log.debug("Request to search graph with tuples query: {}", query);
                });
    }

    @GetMapping(value = "/select", produces = {SparqlMimeTypes.CSV_VALUE, SparqlMimeTypes.JSON_VALUE})
    @ResponseStatus(HttpStatus.OK)
    public Flux<BindingSet> queryBindingsGet(@RequestParam(required = true) String query,
                                          @RequestParam(required = false, defaultValue = "entities", value = "entities") @Parameter(name = "repository", description = "The repository type in which the query should search.")
                                          RepositoryType repositoryType) {

        return super.acquireContext()
                .flatMapMany(ctx -> queryServices.queryValues(query, repositoryType, ctx))
                .doOnSubscribe(s -> {
                    if (log.isDebugEnabled()) log.debug("Request to search graph with tuples query: {}", query);
                });
    }


    @PostMapping(value = "/construct", consumes = "text/plain", produces = {"text/turtle", "application/ld+json"})
    @ResponseStatus(HttpStatus.OK)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Sparql Construct Query",
            content = @Content(examples = {
                    @ExampleObject(name = "Query everything", value = "CONSTRUCT WHERE { ?s ?p ?o . } LIMIT 100")
            })
    )
    public Flux<AnnotatedStatement> queryStatements(@RequestBody String query, @RequestParam(required = false, defaultValue = "entities", value = "entities") @Parameter(name = "repository", description = "The repository type in which the query should search.")
    RepositoryType repositoryType) {

        return acquireContext()
                .flatMapMany(ctx -> queryServices.queryGraph(query, repositoryType, ctx))
                .doOnSubscribe(s -> {
                    if (log.isDebugEnabled()) log.debug("Request to search graph with construct query: {}", query);
                });

    }
}
