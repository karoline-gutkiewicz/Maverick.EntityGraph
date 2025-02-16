package org.av360.maverick.graph.feature.applications.services.model;

import org.eclipse.rdf4j.model.IRI;

/**
 * A subscription is valid for a specific node
 * @param iri
 * @param label
 * @param key
 * @param active
 * @param issueDate
 * @param application
 */
public record Subscription(IRI iri, String label, String key, boolean active, String issueDate, Application application) {



}