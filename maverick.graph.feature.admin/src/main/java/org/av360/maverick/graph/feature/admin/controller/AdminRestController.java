package org.av360.maverick.graph.feature.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.api.controller.AbstractController;
import org.av360.maverick.graph.feature.admin.controller.dto.ImportFromEndpointRequest;
import org.av360.maverick.graph.feature.admin.services.AdminServices;
import org.av360.maverick.graph.model.enums.RdfMimeTypes;
import org.av360.maverick.graph.model.enums.RepositoryType;
import org.av360.maverick.graph.store.rdf.helpers.RdfUtils;
import org.eclipse.rdf4j.rio.RDFParserFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping(path = "/api/admin")
//@Api(tags = "Admin Operations")
@Slf4j(topic = "graph.feat.admin.ctrl.api")
@SecurityRequirement(name = "api_key")
public class AdminRestController extends AbstractController {
    protected final AdminServices adminServices;

    public AdminRestController(AdminServices adminServices) {
        this.adminServices = adminServices;
    }

    //@ApiOperation(value = "Empty repository", tags = {})
    @GetMapping(value = "/reset", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Removes all statements within the repository")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Mono<Void> resetRepository(
            @RequestParam(required = false, defaultValue = "entities", value = "entities") @Parameter(name = "repository", description = "The repository type in which the query should search.")
            RepositoryType repositoryType) {
        Assert.notNull(repositoryType, "Invalid value for repository type: " + repositoryType);

        return super.acquireContext()
                .map(context -> context.updateEnvironment(env -> env.withRepositoryType(repositoryType)))
                .flatMap(adminServices::reset)
                .doOnError(throwable -> log.error("Error while purging repository. Type '{}' with reason: {}", throwable.getClass().getSimpleName(), throwable.getMessage() ))
                .doOnSubscribe(s -> log.info("Request to empty the repository of type '{}'", repositoryType));
    }


    //@ApiOperation(value = "Import RDF into entity repository", tags = {})
    @PostMapping(value = "/import/content", consumes = {RdfMimeTypes.JSONLD_VALUE, RdfMimeTypes.NTRIPLES_VALUE, RdfMimeTypes.TURTLE_VALUE, RdfMimeTypes.N3_VALUE, RdfMimeTypes.RDFXML_VALUE, RdfMimeTypes.BINARY_VALUE, RdfMimeTypes.NQUADS_VALUE, RdfMimeTypes.TURTLESTAR_VALUE})
    @Operation(summary = "Imports rdf content in request body into the target repository")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Mono<Void> importEntities(
            @RequestBody @Parameter(name = "data", description = "The rdf data.") Flux<DataBuffer> bytes,
            // @ApiParam(example = "text/turtle")
            @RequestParam(required = false, defaultValue = "entities", value = "entities") @Parameter(name = "repository", description = "The repository type in which the query should search.")
            RepositoryType repositoryType,
            @RequestHeader(HttpHeaders.CONTENT_TYPE)
            @Parameter(description = "The RDF format of the content",schema = @Schema(type = "string", allowableValues = {"text/turtle", "application/n3", "application/n-triples", "application/rdf+xml", "application/ld+json", "application/n-quads", "application/vnd.hdt"}))
            String mimetype
            ) {
        Assert.isTrue(StringUtils.hasLength(mimetype), "Mimetype is a required parameter");

        return super.acquireContext()
                .map(context -> context.updateEnvironment(env -> env.setRepositoryType(repositoryType)))
                .flatMap(ctx -> adminServices.importEntities(bytes, mimetype, ctx))
                .doOnError(throwable -> log.error("Error while importing to repository.", throwable))
                .doOnSubscribe(s -> log.debug("Request to import a request of mimetype {}", mimetype));
    }

    @PostMapping(value = "/import/endpoint", consumes = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Imports rdf content from sparql endpoint into target repository")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Mono<Void> importFromSparql(
            @RequestBody @Parameter(name = "endpoint", description = "URL to the sparql endpoint.") ImportFromEndpointRequest importFromEndpointRequest,
            @RequestParam(required = false, defaultValue = "entities", value = "entities") @Parameter(name = "repository", description = "The repository type to import to.")
            RepositoryType repositoryType
    ) {

        return super.acquireContext()
                .map(context -> context.updateEnvironment(env -> env.setRepositoryType(repositoryType)))
                .flatMap(ctx -> adminServices.importFromEndpoint(importFromEndpointRequest.endpoint(), importFromEndpointRequest.headers(), 1000, 0, ctx))
                .doOnError(throwable -> log.error("Error while importing to repository.", throwable))
                .doOnSubscribe(s -> log.debug("Request to import a request from endpoint {}", importFromEndpointRequest.endpoint()));
    }

    @PostMapping(value = "/import/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Imports rdf content from file into target repository")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Mono<Void> importFile(
            @RequestPart
            @Parameter(name = "file", description = "The file with rdf data.") Mono<FilePart> fileMono,
            //@ApiParam(example = "text/turtle")
            @RequestParam(required = false, defaultValue = "entities", value = "entities")
            @Parameter(name = "repository", description = "The repository type in which the query should search.")
            RepositoryType repositoryType,
            @RequestParam
            @Parameter(description = "The RDF format of the file",schema = @Schema(type = "string", allowableValues = {"text/turtle", "application/rdf+xml", "application/ld+json", "application/n-quads", "application/vnd.hdt"}))
            String mimetype) {
        Assert.isTrue(StringUtils.hasLength(mimetype), "Mimetype is a required parameter");

        Optional<RDFParserFactory> parserFactory = RdfUtils.getParserFactory(MimeType.valueOf(mimetype));
        Assert.isTrue(parserFactory.isPresent(), "Unsupported mimetype for parsing the file. Supported mimetypes are: " + RdfUtils.getSupportedMimeTypes());

        return super.acquireContext()
                .map(context -> context.getEnvironment().withRepositoryType(repositoryType))
                .flatMap(context -> Mono.zip(Mono.just(context), fileMono))
                .flatMap(pair -> adminServices.importEntities(pair.getT2().content(), mimetype, pair.getT1()))
                .doOnError(throwable -> log.error("Error while importing to repository.", throwable))
                .doOnSubscribe(s -> log.info("Request to import a file of mimetype {}", mimetype));
    }

}
