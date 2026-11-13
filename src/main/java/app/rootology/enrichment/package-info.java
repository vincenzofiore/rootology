/**
 * Knowledge enrichment (#24): members propose missing facts (another name, a crop pair, a pest on a
 * crop). A space fact applies at once and only to its space; a global fact needs a cited source and a
 * curator's approval. Every proposal and review is kept in Postgres; approved facts are written into
 * the knowledge graph, and written again whenever the graph is rebuilt.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Knowledge Enrichment",
        allowedDependencies = { "spaces", "facts" }
)
package app.rootology.enrichment;
