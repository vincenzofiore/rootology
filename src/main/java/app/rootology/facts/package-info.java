/**
 * The pipeline's own fact store: the declared triples of the closed dataset, kept in Neo4j.
 * It is separate from the Jena graph, which only serves the arbiter. Exposes raw triples only,
 * no inference.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Fact Store (Neo4j)",
        allowedDependencies = { "ontology" }
)
package app.rootology.facts;
