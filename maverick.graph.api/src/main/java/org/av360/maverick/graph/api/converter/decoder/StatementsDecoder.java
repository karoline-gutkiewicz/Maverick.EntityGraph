package org.av360.maverick.graph.api.converter.decoder;

import lombok.extern.slf4j.Slf4j;
import org.av360.maverick.graph.model.rdf.Triples;
import org.av360.maverick.graph.store.rdf.helpers.RdfUtils;
import org.av360.maverick.graph.store.rdf.helpers.TriplesCollector;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "graph.ctrl.io.decoder")
public class StatementsDecoder implements Decoder<Triples> {
    private static final List<MimeType> mimeTypes;

    static {
        mimeTypes = List.of(
                MimeType.valueOf(RDFFormat.JSONLD.getDefaultMIMEType()),
                MimeType.valueOf(RDFFormat.RDFJSON.getDefaultMIMEType()),
                MimeType.valueOf(RDFFormat.NTRIPLES.getDefaultMIMEType()),
                MimeType.valueOf(RDFFormat.N3.getDefaultMIMEType()),
                MimeType.valueOf(RDFFormat.TURTLE.getDefaultMIMEType()),
                MimeType.valueOf(RDFFormat.NQUADS.getDefaultMIMEType())
        );
    }

    @Override
    public List<MimeType> getDecodableMimeTypes() {
        return mimeTypes;
    }

    @Override
    public boolean canDecode(ResolvableType elementType, MimeType mimeType) {
        return mimeType != null && Triples.class.isAssignableFrom(elementType.toClass()) && mimeType.isPresentIn(mimeTypes);
    }

    @Override
    public Flux<Triples> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType, MimeType mimeType, Map<String, Object> hints) {
        return Flux.from(this.parse(inputStream, mimeType));
    }

    @Override
    public Mono<Triples> decodeToMono(Publisher<DataBuffer> inputStream, ResolvableType elementType, MimeType mimeType, Map<String, Object> hints) {
        return this.parse(inputStream, mimeType);
    }


    private Mono<Triples> parse(Publisher<DataBuffer> publisher, MimeType mimeType) {

        return DataBufferUtils.join(publisher)
                .flatMap(dataBuffer -> {
                    RDFParser parser = RdfUtils.getParserFactory(mimeType).orElseThrow().getParser();
                    TriplesCollector handler = RdfUtils.getTriplesCollector();

                    try (InputStream is = dataBuffer.asInputStream(true)) {
                        var result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        log.info(result);

                        parser.setRDFHandler(handler);
                        parser.parse(new StringReader(result));
                        log.debug("Parsed payload of mimetype '{}' with {} statements", mimeType.toString(), handler.getTriples().getModel().size());
                        return Mono.just(handler.getTriples());
                    } catch (Exception e) {
                        log.warn("Failed to parse request of mimetype '{}'", mimeType);
                        return Mono.error(e);
                    }
                });
    }


}
