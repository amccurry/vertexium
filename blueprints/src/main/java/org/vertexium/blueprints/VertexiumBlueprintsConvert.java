package org.vertexium.blueprints;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

public class VertexiumBlueprintsConvert {
    public static org.vertexium.Vertex toVertexium(Vertex vertex) {
        if (vertex == null) {
            return null;
        }
        if (vertex instanceof VertexiumBlueprintsVertex) {
            return ((VertexiumBlueprintsVertex) vertex).getVertexiumElement();
        }
        throw new IllegalArgumentException("Invalid vertex, cannot get Vertexium vertex. " + vertex.getClass().getName());
    }

    public static org.vertexium.Edge toVertexium(Edge edge) {
        if (edge == null) {
            return null;
        }
        if (edge instanceof VertexiumBlueprintsEdge) {
            return ((VertexiumBlueprintsEdge) edge).getVertexiumElement();
        }
        throw new IllegalArgumentException("Invalid edge, cannot get Vertexium edge. " + edge.getClass().getName());
    }

    public static org.vertexium.Direction toVertexium(Direction direction) {
        switch (direction) {
            case OUT:
                return org.vertexium.Direction.OUT;
            case IN:
                return org.vertexium.Direction.IN;
            case BOTH:
                return org.vertexium.Direction.BOTH;
            default:
                throw new IllegalArgumentException("Unexpected direction: " + direction);
        }
    }

    public static String idToString(Object id) {
        if (id == null) {
            return null;
        }
        if (id instanceof String) {
            return (String) id;
        }
        return id.toString();
    }
}
