package org.neolumin.vertexium.cli.model;

import org.neolumin.vertexium.*;
import org.neolumin.vertexium.cli.VertexiumScript;

import java.io.PrintWriter;
import java.io.StringWriter;

public class LazyVertex extends ModelBase {
    private final String vertexId;

    public LazyVertex(String vertexId) {
        this.vertexId = vertexId;
    }

    @Override
    public String toString() {
        Vertex v = getV();
        if (v == null) {
            return null;
        }

        return toString(v, getAuthorizations());
    }

    public static String toString(Vertex v, Authorizations authorizations) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        writer.println("@|bold " + v.getId() + "|@");
        writer.println("  @|bold visibility:|@ " + v.getVisibility());

        writer.println("  @|bold properties:|@");
        VertexiumScript.getContextProperties().clear();
        int propIndex = 0;
        for (Property prop : v.getProperties()) {
            String propertyIndexString = "p" + propIndex;
            String valueString = VertexiumScript.valueToString(prop.getValue(), false);
            writer.println("    @|bold " + propertyIndexString + ":|@ " + prop.getName() + ":" + prop.getKey() + "[" + prop.getVisibility().getVisibilityString() + "] = " + valueString);
            LazyProperty lazyProperty = new LazyVertexProperty(v.getId(), prop.getKey(), prop.getName(), prop.getVisibility());
            VertexiumScript.getContextProperties().put(propertyIndexString, lazyProperty);
            propIndex++;
        }

        VertexiumScript.getContextEdges().clear();
        int edgeIndex = 0;

        writer.println("  @|bold out edges:|@");
        for (Edge edge : v.getEdges(Direction.OUT, authorizations)) {
            String edgeIndexString = "e" + edgeIndex;
            writer.println("    @|bold " + edgeIndexString + ":|@ " + edge.getId() + ": " + edge.getLabel() + " -> " + edge.getOtherVertexId(v.getId()));
            LazyEdge lazyEdge = new LazyEdge(edge.getId());
            VertexiumScript.getContextEdges().put(edgeIndexString, lazyEdge);
            edgeIndex++;
        }

        writer.println("  @|bold in edges:|@");
        for (Edge edge : v.getEdges(Direction.IN, authorizations)) {
            String edgeIndexString = "e" + edgeIndex;
            writer.println("    @|bold " + edgeIndexString + ":|@ " + edge.getId() + ": " + edge.getLabel() + " -> " + edge.getOtherVertexId(v.getId()));
            LazyEdge lazyEdge = new LazyEdge(edge.getId());
            VertexiumScript.getContextEdges().put(edgeIndexString, lazyEdge);
            edgeIndex++;
        }

        return out.toString();
    }

    private Vertex getV() {
        return getGraph().getVertex(getId(), getAuthorizations());
    }

    public String getId() {
        return vertexId;
    }
}
