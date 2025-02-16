package org.av360.maverick.graph.api.controller.entities;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.av360.maverick.graph.api.controller.AbstractController;
import org.av360.maverick.graph.model.api.DetailsAPI;
import org.av360.maverick.graph.model.enums.RdfMimeTypes;
import org.av360.maverick.graph.model.rdf.AnnotatedStatement;
import org.av360.maverick.graph.model.rdf.Triples;
import org.av360.maverick.graph.services.EntityServices;
import org.av360.maverick.graph.services.ValueServices;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping(path = "/api")
//@Api(tags = "Values")
@Slf4j(topic = "graph.api.ctrl.details")
@SecurityRequirement(name = "api_key")
@Tag(name = "Details")
public class DetailsController extends AbstractController implements DetailsAPI {



    protected final ValueServices values;

    protected final EntityServices entities;

    public DetailsController(ValueServices values, EntityServices entities) {
        this.values = values;
        this.entities = entities;
    }

    @Override
    @Operation(summary = "Returns all details for a value or link")
    @GetMapping(value = "/entities/{id:[\\w|\\d|\\-|\\_]+}/{type}/{prefixedValueKey:[\\w|\\d]+\\.[\\w|\\d]+}/details",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = {RdfMimeTypes.TURTLE_VALUE, RdfMimeTypes.JSONLD_VALUE})
    @ResponseStatus(HttpStatus.OK)
    public Flux<AnnotatedStatement> getDetails(
            @PathVariable @Parameter(name = "entity identifier") String id,
            @PathVariable(required = true, value = "values") @Parameter(name = "property type") PropertyType type,
            @PathVariable String prefixedValueKey,
            @RequestParam(required = false) boolean hash
    ) {
        return Flux.error(new NotImplementedException("Method has not been implemented yet."));
    }

    @Override
    @Operation(summary = "Purge all details for a value")
    @DeleteMapping(value = "/entities/{id:[\\w|\\d|\\-|\\_]+}/values/{prefixedValueKey:[\\w|\\d]+\\.[\\w|\\d]+}/details",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = {RdfMimeTypes.TURTLE_VALUE, RdfMimeTypes.JSONLD_VALUE})
    @ResponseStatus(HttpStatus.OK)
    public Flux<AnnotatedStatement> purgeDetails(
            @PathVariable @Parameter(name = "entity identifier") String id,
            @PathVariable String prefixedValueKey
    ) {
        return Flux.error(new NotImplementedException("Method has not been implemented yet."));
    }

    @Override
    @Operation(summary = "Delete a specific detail for a value")
    @DeleteMapping(value = "/entities/{id:[\\w|\\d|\\-|\\_]+}/values/{prefixedValueKey:[\\w|\\d]+\\.[\\w|\\d]+}/details/{prefixedDetailKey:[\\w|\\d]+\\.[\\w|\\d]+}",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = {RdfMimeTypes.TURTLE_VALUE, RdfMimeTypes.JSONLD_VALUE})
    @ResponseStatus(HttpStatus.OK)
    public Flux<AnnotatedStatement> deleteDetail(
            @PathVariable @Parameter(name = "entity identifier") String id,
            @PathVariable String prefixedValueKey,
            @PathVariable String prefixedDetailKey,
            @RequestParam(required = false) boolean multiple,
            @RequestParam(required = false) String hash
    ) {
        return Flux.error(new NotImplementedException("Method has not been implemented yet."));
    }

    @Override
    @Operation(summary = "Creates a statement about a statement use the post body as value.")
    @PostMapping(value = "/entities/{id:[\\w|\\d|\\-|\\_]+}/{type}/{prefixedValueKey:[\\w|\\d]+\\.[\\w|\\d]+}/details/{prefixedDetailKey:[\\w|\\d]+\\.[\\w|\\d]+}",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = {RdfMimeTypes.TURTLE_VALUE, RdfMimeTypes.JSONLD_VALUE})
    @ResponseStatus(HttpStatus.OK)
    public Flux<AnnotatedStatement> createDetail(
            @PathVariable @Parameter(name = "entity identifier") String id,
            @PathVariable(required = true, value = "values") @Parameter(name = "property type") PropertyType type,
            @PathVariable String prefixedValueKey,
            @PathVariable String prefixedDetailKey
    ) {
        return Flux.error(new NotImplementedException("Method has not been implemented yet."));
    }



    @Override
    @Operation(summary = "Creates a statement about a statement use the post body as value.")
    @PostMapping(value = "/entities/{id:[\\w|\\d|\\-|\\_]+}/{type}/{prefixedValueKey:[\\w|\\d]+\\.[\\w|\\d]+}/details",
            consumes = RdfMimeTypes.TURTLESTAR_VALUE,
            produces = {RdfMimeTypes.TURTLE_VALUE, RdfMimeTypes.JSONLD_VALUE})
    @ResponseStatus(HttpStatus.OK)
    public Flux<AnnotatedStatement> createLinkDetail(
            @PathVariable @Parameter(name = "entity identifier") String id,
            @PathVariable(required = true, value = "values") @Parameter(name = "property type") PropertyType type,
            @PathVariable String prefixedValueKey,
            @RequestBody Triples request
    ) {
        return Flux.error(new NotImplementedException("Method has not been implemented yet."));
    }


}

