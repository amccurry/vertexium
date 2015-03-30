package org.neolumin.vertexium.query;

import org.neolumin.vertexium.*;
import org.neolumin.vertexium.util.VerticesToEdgeIdsIterable;

import java.util.EnumSet;
import java.util.Map;

import static org.neolumin.vertexium.util.IterableUtils.toIterable;

public class DefaultMultiVertexQuery extends QueryBase implements MultiVertexQuery {
    private final String[] vertexIds;

    public DefaultMultiVertexQuery(Graph graph, String[] vertexIds, String queryString, Map<String, PropertyDefinition> propertyDefinitions, Authorizations authorizations) {
        super(graph, queryString, propertyDefinitions, authorizations);
        this.vertexIds = vertexIds;
    }

    @Override
    public Iterable<Vertex> vertices(EnumSet<FetchHint> fetchHints) {
        Iterable<Vertex> vertices = getGraph().getVertices(toIterable(getVertexIds()), fetchHints, getParameters().getAuthorizations());
        return SortedIterable.wrapIfNeeded(
                new DefaultGraphQueryIterable<>(getParameters(), vertices, true, true),
                getParameters().getSortParameters()
        );
    }

    @Override
    public Iterable<Edge> edges(EnumSet<FetchHint> fetchHints) {
        Iterable<Vertex> vertices = getGraph().getVertices(toIterable(getVertexIds()), fetchHints, getParameters().getAuthorizations());
        Iterable<String> edgeIds = new VerticesToEdgeIdsIterable(vertices, getParameters().getAuthorizations());
        Iterable<Edge> edges = getGraph().getEdges(edgeIds, fetchHints, getParameters().getAuthorizations());
        return SortedIterable.wrapIfNeeded(
                new DefaultGraphQueryIterable<>(getParameters(), edges, true, true),
                getParameters().getSortParameters()
        );
    }

    public String[] getVertexIds() {
        return vertexIds;
    }
}
