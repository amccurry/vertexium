package org.vertexium.blueprints;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.VertexQuery;
import com.tinkerpop.blueprints.util.DefaultVertexQuery;
import org.vertexium.Authorizations;
import org.vertexium.util.ConvertingIterable;

public class VertexiumBlueprintsVertex extends VertexiumBlueprintsElement implements Vertex {
    protected VertexiumBlueprintsVertex(VertexiumBlueprintsGraph graph, org.vertexium.Vertex vertex, Authorizations authorizations) {
        super(graph, vertex, authorizations);
    }

    public static Vertex create(VertexiumBlueprintsGraph graph, org.vertexium.Vertex vertex, Authorizations authorizations) {
        if (vertex == null) {
            return null;
        }
        return new VertexiumBlueprintsVertex(graph, vertex, authorizations);
    }

    @Override
    public Iterable<Edge> getEdges(Direction direction, final String... labels) {
        final org.vertexium.Direction sgDirection = VertexiumBlueprintsConvert.toVertexium(direction);
        final Authorizations authorizations = getGraph().getAuthorizationsProvider().getAuthorizations();
        return new ConvertingIterable<org.vertexium.Edge, Edge>(getVertexiumElement().getEdges(sgDirection, labels, authorizations)) {
            @Override
            protected Edge convert(org.vertexium.Edge edge) {
                return VertexiumBlueprintsEdge.create(getGraph(), edge, authorizations);
            }
        };
    }

    @Override
    public Iterable<Vertex> getVertices(Direction direction, final String... labels) {
        final org.vertexium.Direction sgDirection = VertexiumBlueprintsConvert.toVertexium(direction);
        final Authorizations authorizations = getGraph().getAuthorizationsProvider().getAuthorizations();
        return new ConvertingIterable<org.vertexium.Vertex, Vertex>(getVertexiumElement().getVertices(sgDirection, labels, authorizations)) {
            @Override
            protected Vertex convert(org.vertexium.Vertex vertex) {
                return VertexiumBlueprintsVertex.create(getGraph(), vertex, authorizations);
            }
        };
    }

    @Override
    public VertexQuery query() {
        return new DefaultVertexQuery(this); // TODO implement
    }

    @Override
    public Edge addEdge(String label, Vertex inVertex) {
        if (label == null) {
            throw new IllegalArgumentException("Cannot add edge with null label");
        }
        return getGraph().addEdge(null, this, inVertex, label);
    }

    @Override
    public void remove() {
        getGraph().removeVertex(this);
    }

    @Override
    public org.vertexium.Vertex getVertexiumElement() {
        return (org.vertexium.Vertex) super.getVertexiumElement();
    }
}
