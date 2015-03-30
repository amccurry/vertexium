package org.neolumin.vertexium.test;


import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.neolumin.vertexium.*;
import org.neolumin.vertexium.event.*;
import org.neolumin.vertexium.mutation.ElementMutation;
import org.neolumin.vertexium.mutation.ExistingElementMutation;
import org.neolumin.vertexium.property.PropertyValue;
import org.neolumin.vertexium.property.StreamingPropertyValue;
import org.neolumin.vertexium.query.*;
import org.neolumin.vertexium.search.DefaultSearchIndex;
import org.neolumin.vertexium.search.IndexHint;
import org.neolumin.vertexium.search.SearchIndex;
import org.neolumin.vertexium.test.util.LargeStringInputStream;
import org.neolumin.vertexium.type.GeoCircle;
import org.neolumin.vertexium.type.GeoPoint;
import org.neolumin.vertexium.util.ConvertingIterable;
import org.neolumin.vertexium.util.IterableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static org.junit.Assert.*;
import static org.neolumin.vertexium.util.IterableUtils.toList;

@RunWith(JUnit4.class)
public abstract class GraphTestBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphTestBase.class);
    public static final String VISIBILITY_A_STRING = "a";
    public static final String VISIBILITY_B_STRING = "b";
    public static final String VISIBILITY_C_STRING = "c";
    public static final String VISIBILITY_MIXED_CASE_STRING = "MIXED_CASE_a";
    public static final Visibility VISIBILITY_A = new Visibility(VISIBILITY_A_STRING);
    public static final Visibility VISIBILITY_A_AND_B = new Visibility("a&b");
    public static final Visibility VISIBILITY_B = new Visibility("b");
    public static final Visibility VISIBILITY_MIXED_CASE_a = new Visibility("((MIXED_CASE_a))|b");
    public static final Visibility VISIBILITY_EMPTY = new Visibility("");
    public final Authorizations AUTHORIZATIONS_A;
    public final Authorizations AUTHORIZATIONS_B;
    public final Authorizations AUTHORIZATIONS_C;
    public final Authorizations AUTHORIZATIONS_MIXED_CASE_a_AND_B;
    public final Authorizations AUTHORIZATIONS_A_AND_B;
    public final Authorizations AUTHORIZATIONS_EMPTY;
    public static final int LARGE_PROPERTY_VALUE_SIZE = 1024 + 1;

    protected Graph graph;
    protected List<GraphEvent> graphEvents;

    protected abstract Graph createGraph() throws Exception;

    public Graph getGraph() {
        return graph;
    }

    public GraphTestBase() {
        AUTHORIZATIONS_A = createAuthorizations("a");
        AUTHORIZATIONS_B = createAuthorizations("b");
        AUTHORIZATIONS_C = createAuthorizations("c");
        AUTHORIZATIONS_A_AND_B = createAuthorizations("a", "b");
        AUTHORIZATIONS_MIXED_CASE_a_AND_B = createAuthorizations("MIXED_CASE_a", "b");
        AUTHORIZATIONS_EMPTY = createAuthorizations();
    }

    protected abstract Authorizations createAuthorizations(String... auths);

    @Before
    public void before() throws Exception {
        graph = createGraph();
        graphEvents = new ArrayList<>();
        graph.addGraphEventListener(new GraphEventListener() {
            @Override
            public void onGraphEvent(GraphEvent graphEvent) {
                graphEvents.add(graphEvent);
            }
        });
    }

    @After
    public void after() throws Exception {
        graph.shutdown();
        graph = null;
    }

    @Test
    public void testAddVertexWithId() {
        Vertex vertexAdded = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        assertNotNull(vertexAdded);
        assertEquals("v1", vertexAdded.getId());
        graph.flush();

        Vertex v = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertNotNull(v);
        assertEquals("v1", v.getId());
        assertEquals(VISIBILITY_A, v.getVisibility());

        v = graph.getVertex("", AUTHORIZATIONS_A);
        assertNull(v);

        v = graph.getVertex(null, AUTHORIZATIONS_A);
        assertNull(v);

        assertEvents(
                new AddVertexEvent(graph, vertexAdded)
        );
    }

    @Test
    public void testAddVertexWithoutId() {
        Vertex vertexAdded = graph.addVertex(VISIBILITY_A, AUTHORIZATIONS_A);
        assertNotNull(vertexAdded);
        String vertexId = vertexAdded.getId();
        assertNotNull(vertexId);
        graph.flush();

        Vertex v = graph.getVertex(vertexId, AUTHORIZATIONS_A);
        assertNotNull(v);
        assertNotNull(vertexId);

        assertEvents(
                new AddVertexEvent(graph, vertexAdded)
        );
    }

    @Test
    public void testAddStreamingPropertyValue() throws IOException, InterruptedException {
        String expectedLargeValue = IOUtils.toString(new LargeStringInputStream(LARGE_PROPERTY_VALUE_SIZE));
        PropertyValue propSmall = new StreamingPropertyValue(new ByteArrayInputStream("value1".getBytes()), String.class);
        PropertyValue propLarge = new StreamingPropertyValue(new ByteArrayInputStream(expectedLargeValue.getBytes()), String.class);
        String largePropertyName = "propLarge/\\*!@#$%^&*()[]{}|";
        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("propSmall", propSmall, VISIBILITY_A)
                .setProperty(largePropertyName, propLarge, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Object> propSmallValues = v1.getPropertyValues("propSmall");
        Assert.assertEquals(1, IterableUtils.count(propSmallValues));
        Object propSmallValue = propSmallValues.iterator().next();
        assertTrue("propSmallValue was " + propSmallValue.getClass().getName(), propSmallValue instanceof StreamingPropertyValue);
        StreamingPropertyValue value = (StreamingPropertyValue) propSmallValue;
        assertEquals(String.class, value.getValueType());
        assertEquals("value1".getBytes().length, value.getLength());
        assertEquals("value1", IOUtils.toString(value.getInputStream()));
        assertEquals("value1", IOUtils.toString(value.getInputStream()));

        Iterable<Object> propLargeValues = v1.getPropertyValues(largePropertyName);
        Assert.assertEquals(1, IterableUtils.count(propLargeValues));
        Object propLargeValue = propLargeValues.iterator().next();
        assertTrue(largePropertyName + " was " + propLargeValue.getClass().getName(), propLargeValue instanceof StreamingPropertyValue);
        value = (StreamingPropertyValue) propLargeValue;
        assertEquals(String.class, value.getValueType());
        assertEquals(expectedLargeValue.getBytes().length, value.getLength());
        assertEquals(expectedLargeValue, IOUtils.toString(value.getInputStream()));
        assertEquals(expectedLargeValue, IOUtils.toString(value.getInputStream()));

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        propSmallValues = v1.getPropertyValues("propSmall");
        Assert.assertEquals(1, IterableUtils.count(propSmallValues));
        propSmallValue = propSmallValues.iterator().next();
        assertTrue("propSmallValue was " + propSmallValue.getClass().getName(), propSmallValue instanceof StreamingPropertyValue);
        value = (StreamingPropertyValue) propSmallValue;
        assertEquals(String.class, value.getValueType());
        assertEquals("value1".getBytes().length, value.getLength());
        assertEquals("value1", IOUtils.toString(value.getInputStream()));
        assertEquals("value1", IOUtils.toString(value.getInputStream()));

        propLargeValues = v1.getPropertyValues(largePropertyName);
        Assert.assertEquals(1, IterableUtils.count(propLargeValues));
        propLargeValue = propLargeValues.iterator().next();
        assertTrue(largePropertyName + " was " + propLargeValue.getClass().getName(), propLargeValue instanceof StreamingPropertyValue);
        value = (StreamingPropertyValue) propLargeValue;
        assertEquals(String.class, value.getValueType());
        assertEquals(expectedLargeValue.getBytes().length, value.getLength());
        assertEquals(expectedLargeValue, IOUtils.toString(value.getInputStream()));
        assertEquals(expectedLargeValue, IOUtils.toString(value.getInputStream()));
    }

    @Test
    public void testAddVertexPropertyWithMetadata() {
        Metadata prop1Metadata = new Metadata();
        prop1Metadata.add("metadata1", "metadata1Value", VISIBILITY_A);

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", prop1Metadata, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Vertex v = graph.getVertex("v1", AUTHORIZATIONS_A);
        if (v instanceof HasTimestamp) {
            assertTrue("timestamp should be more than 0", ((HasTimestamp) v).getTimestamp() > 0);
        }

        Assert.assertEquals(1, IterableUtils.count(v.getProperties("prop1")));
        Property prop1 = v.getProperties("prop1").iterator().next();
        if (prop1 instanceof HasTimestamp) {
            assertTrue("timestamp should be more than 0", ((HasTimestamp) prop1).getTimestamp() > 0);
        }

        prop1Metadata = prop1.getMetadata();
        assertNotNull(prop1Metadata);
        assertEquals(1, prop1Metadata.entrySet().size());
        assertEquals("metadata1Value", prop1Metadata.getEntry("metadata1", VISIBILITY_A).getValue());

        prop1Metadata.add("metadata2", "metadata2Value", VISIBILITY_A);
        v.prepareMutation()
                .setProperty("prop1", "value1", prop1Metadata, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v.getProperties("prop1")));
        prop1 = v.getProperties("prop1").iterator().next();
        prop1Metadata = prop1.getMetadata();
        assertEquals(2, prop1Metadata.entrySet().size());
        assertEquals("metadata1Value", prop1Metadata.getEntry("metadata1", VISIBILITY_A).getValue());
        assertEquals("metadata2Value", prop1Metadata.getEntry("metadata2", VISIBILITY_A).getValue());

        // make sure that when we update the value the metadata does not get destroyed
        prop1Metadata = new Metadata();
        v.setProperty("prop1", "value1", prop1Metadata, VISIBILITY_A, AUTHORIZATIONS_A_AND_B);

        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v.getProperties("prop1")));
        prop1 = v.getProperties("prop1").iterator().next();
        prop1Metadata = prop1.getMetadata();
        assertEquals(2, prop1Metadata.entrySet().size());
    }

    @Test
    public void testAddVertexWithProperties() {
        Vertex vertexAdded = graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_A)
                .setProperty("prop2", "value2", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(1, IterableUtils.count(vertexAdded.getProperties("prop1")));
        assertEquals("value1", vertexAdded.getPropertyValues("prop1").iterator().next());
        Assert.assertEquals(1, IterableUtils.count(vertexAdded.getProperties("prop2")));
        assertEquals("value2", vertexAdded.getPropertyValues("prop2").iterator().next());
        graph.flush();

        Vertex v = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(1, IterableUtils.count(v.getProperties("prop1")));
        assertEquals("value1", v.getPropertyValues("prop1").iterator().next());
        Assert.assertEquals(1, IterableUtils.count(v.getProperties("prop2")));
        assertEquals("value2", v.getPropertyValues("prop2").iterator().next());

        assertEvents(
                new AddVertexEvent(graph, vertexAdded),
                new AddPropertyEvent(graph, vertexAdded, vertexAdded.getProperty("prop1")),
                new AddPropertyEvent(graph, vertexAdded, vertexAdded.getProperty("prop2"))
        );
        graphEvents.clear();

        v = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        vertexAdded = v.prepareMutation()
                .addPropertyValue("key1", "prop1Mutation", "value1Mutation", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();
        v = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(1, IterableUtils.count(v.getProperties("prop1Mutation")));
        assertEquals("value1Mutation", v.getPropertyValues("prop1Mutation").iterator().next());
        assertEvents(
                new AddPropertyEvent(graph, vertexAdded, vertexAdded.getProperty("prop1Mutation"))
        );
    }

    @Test
    public void testNullPropertyValue() {
        try {
            graph.prepareVertex("v1", VISIBILITY_EMPTY)
                    .setProperty("prop1", null, VISIBILITY_A)
                    .save(AUTHORIZATIONS_A_AND_B);
            throw new VertexiumException("expected null check");
        } catch (NullPointerException ex) {
            assertTrue(ex.getMessage().contains("prop1"));
        }
    }

    @Test
    public void testConcurrentModificationOfProperties() {
        Vertex v = graph.prepareVertex("v1", VISIBILITY_EMPTY)
                .setProperty("prop1", "value1", VISIBILITY_A)
                .setProperty("prop2", "value2", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        int i = 0;
        for (Property p : v.getProperties()) {
            assertNotNull(p.toString());
            if (i == 0) {
                v.setProperty("prop3", "value3", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);
            }
            i++;
        }
    }

    @Test
    public void testAddVertexWithPropertiesWithTwoDifferentVisibilities() {
        Vertex v = graph.prepareVertex("v1", VISIBILITY_EMPTY)
                .setProperty("prop1", "value1a", VISIBILITY_A)
                .setProperty("prop1", "value1b", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(2, IterableUtils.count(v.getProperties("prop1")));

        v = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(2, IterableUtils.count(v.getProperties("prop1")));

        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v.getProperties("prop1")));
        assertEquals("value1a", v.getPropertyValue("prop1"));

        v = graph.getVertex("v1", AUTHORIZATIONS_B);
        Assert.assertEquals(1, IterableUtils.count(v.getProperties("prop1")));
        assertEquals("value1b", v.getPropertyValue("prop1"));
    }

    @Test
    public void testMultivaluedProperties() {
        Vertex v = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);

        v.prepareMutation()
                .addPropertyValue("propid1a", "prop1", "value1a", VISIBILITY_A)
                .addPropertyValue("propid2a", "prop2", "value2a", VISIBILITY_A)
                .addPropertyValue("propid3a", "prop3", "value3a", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertEquals("value1a", v.getPropertyValues("prop1").iterator().next());
        assertEquals("value2a", v.getPropertyValues("prop2").iterator().next());
        assertEquals("value3a", v.getPropertyValues("prop3").iterator().next());
        Assert.assertEquals(3, IterableUtils.count(v.getProperties()));

        v.prepareMutation()
                .addPropertyValue("propid1a", "prop1", "value1b", VISIBILITY_A)
                .addPropertyValue("propid2a", "prop2", "value2b", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v.getPropertyValues("prop1")));
        assertEquals("value1b", v.getPropertyValues("prop1").iterator().next());
        Assert.assertEquals(1, IterableUtils.count(v.getPropertyValues("prop2")));
        assertEquals("value2b", v.getPropertyValues("prop2").iterator().next());
        Assert.assertEquals(1, IterableUtils.count(v.getPropertyValues("prop3")));
        assertEquals("value3a", v.getPropertyValues("prop3").iterator().next());
        Assert.assertEquals(3, IterableUtils.count(v.getProperties()));

        v.addPropertyValue("propid1b", "prop1", "value1a-new", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);
        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        org.neolumin.vertexium.test.util.IterableUtils.assertContains("value1b", v.getPropertyValues("prop1"));
        org.neolumin.vertexium.test.util.IterableUtils.assertContains("value1a-new", v.getPropertyValues("prop1"));
        Assert.assertEquals(4, IterableUtils.count(v.getProperties()));
    }

    @Test
    public void testMultivaluedPropertyOrder() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .addPropertyValue("a", "prop", "a", VISIBILITY_A)
                .addPropertyValue("aa", "prop", "aa", VISIBILITY_A)
                .addPropertyValue("b", "prop", "b", VISIBILITY_A)
                .addPropertyValue("0", "prop", "0", VISIBILITY_A)
                .addPropertyValue("A", "prop", "A", VISIBILITY_A)
                .addPropertyValue("Z", "prop", "Z", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Vertex v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertEquals("0", v1.getPropertyValue("prop", 0));
        assertEquals("A", v1.getPropertyValue("prop", 1));
        assertEquals("Z", v1.getPropertyValue("prop", 2));
        assertEquals("a", v1.getPropertyValue("prop", 3));
        assertEquals("aa", v1.getPropertyValue("prop", 4));
        assertEquals("b", v1.getPropertyValue("prop", 5));
    }

    @Test
    public void testRemoveProperty() {
        Vertex v = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);

        v.prepareMutation()
                .addPropertyValue("propid1a", "prop1", "value1a", VISIBILITY_A)
                .addPropertyValue("propid1b", "prop1", "value1b", VISIBILITY_A)
                .addPropertyValue("propid2a", "prop2", "value2a", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();
        this.graphEvents.clear();

        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        Property prop1_propid1a = v.getProperty("propid1a", "prop1");
        Property prop1_propid1b = v.getProperty("propid1b", "prop1");
        v.removeProperty("prop1", AUTHORIZATIONS_A_AND_B);
        graph.flush();
        Assert.assertEquals(1, IterableUtils.count(v.getProperties()));
        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v.getProperties()));

        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A_AND_B).has("prop2", "value2a").vertices()));
        Assert.assertEquals(0, IterableUtils.count(graph.query(AUTHORIZATIONS_A_AND_B).has("prop1", "value1a").vertices()));
        assertEvents(
                new RemovePropertyEvent(graph, v, prop1_propid1a),
                new RemovePropertyEvent(graph, v, prop1_propid1b)
        );
        this.graphEvents.clear();

        Property prop2_propid2a = v.getProperty("propid2a", "prop2");
        v.removeProperty("propid2a", "prop2", AUTHORIZATIONS_A_AND_B);
        graph.flush();
        Assert.assertEquals(0, IterableUtils.count(v.getProperties()));
        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(v.getProperties()));

        assertEvents(
                new RemovePropertyEvent(graph, v, prop2_propid2a)
        );
    }

    @Test
    public void testRemovePropertyWithMutation() {
        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_A)
                .addPropertyValue("propid1a", "prop1", "value1a", VISIBILITY_A)
                .addPropertyValue("propid1b", "prop1", "value1b", VISIBILITY_A)
                .addPropertyValue("propid2a", "prop2", "value2a", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.prepareEdge("e1", v1, v2, "edge1", VISIBILITY_A)
                .addPropertyValue("key1", "prop1", "value1", VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.flush();
        this.graphEvents.clear();

        // remove multiple properties
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Property prop1_propid1a = v1.getProperty("propid1a", "prop1");
        Property prop1_propid1b = v1.getProperty("propid1b", "prop1");
        v1.prepareMutation()
                .removeProperties("prop1")
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();
        Assert.assertEquals(1, IterableUtils.count(v1.getProperties()));
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v1.getProperties()));

        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A_AND_B).has("prop2", "value2a").vertices()));
        Assert.assertEquals(0, IterableUtils.count(graph.query(AUTHORIZATIONS_A_AND_B).has("prop1", "value1a").vertices()));
        assertEvents(
                new RemovePropertyEvent(graph, v1, prop1_propid1a),
                new RemovePropertyEvent(graph, v1, prop1_propid1b)
        );
        this.graphEvents.clear();

        // remove property with key and name
        Property prop2_propid2a = v1.getProperty("propid2a", "prop2");
        v1.prepareMutation()
                .removeProperties("propid2a", "prop2")
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();
        Assert.assertEquals(0, IterableUtils.count(v1.getProperties()));
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(v1.getProperties()));
        assertEvents(
                new RemovePropertyEvent(graph, v1, prop2_propid2a)
        );
        this.graphEvents.clear();

        // remove property from edge
        Edge e1 = graph.getEdge("e1", AUTHORIZATIONS_A);
        Property edgeProperty = e1.getProperty("key1", "prop1");
        e1.prepareMutation()
                .removeProperties("key1", "prop1")
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();
        Assert.assertEquals(0, IterableUtils.count(e1.getProperties()));
        e1 = graph.getEdge("e1", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(e1.getProperties()));
        assertEvents(
                new RemovePropertyEvent(graph, e1, edgeProperty)
        );
    }

    @Test
    public void testRemoveElement() {
        Vertex v = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);

        v.prepareMutation()
                .setProperty("prop1", "value1", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();

        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertNotNull(v);
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A_AND_B).has("prop1", "value1").vertices()));

        graph.removeVertex(v, AUTHORIZATIONS_A_AND_B);
        graph.flush();

        v = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertNull(v);
        Assert.assertEquals(0, IterableUtils.count(graph.query(AUTHORIZATIONS_A_AND_B).has("prop1", "value1").vertices()));
    }

    @Test
    public void testAddVertexWithVisibility() {
        graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addVertex("v2", VISIBILITY_B, AUTHORIZATIONS_A);

        Iterable<Vertex> cVertices = graph.getVertices(AUTHORIZATIONS_C);
        Assert.assertEquals(0, IterableUtils.count(cVertices));

        Iterable<Vertex> aVertices = graph.getVertices(AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(aVertices));
        assertEquals("v1", aVertices.iterator().next().getId());

        Iterable<Vertex> bVertices = graph.getVertices(AUTHORIZATIONS_B);
        Assert.assertEquals(1, IterableUtils.count(bVertices));
        assertEquals("v2", bVertices.iterator().next().getId());

        Iterable<Vertex> allVertices = graph.getVertices(AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(2, IterableUtils.count(allVertices));
    }

    @Test
    public void testAddMultipleVertices() {
        List<ElementBuilder<Vertex>> elements = new ArrayList<>();
        elements.add(graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "v1", VISIBILITY_A));
        elements.add(graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("prop1", "v2", VISIBILITY_A));
        Iterable<Vertex> vertices = graph.addVertices(elements, AUTHORIZATIONS_A_AND_B);
        assertVertexIds(vertices, new String[]{"v1", "v2"});

        if (graph instanceof GraphBaseWithSearchIndex) {
            ((GraphBaseWithSearchIndex) graph).getSearchIndex().addElements(graph, vertices, AUTHORIZATIONS_A_AND_B);
        }
    }

    @Test
    public void testGetVerticesWithIds() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "v1", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v1b", VISIBILITY_A)
                .setProperty("prop1", "v1b", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("prop1", "v2", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v3", VISIBILITY_A)
                .setProperty("prop1", "v3", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        List<String> ids = new ArrayList<>();
        ids.add("v2");
        ids.add("v1");

        Iterable<Vertex> vertices = graph.getVertices(ids, AUTHORIZATIONS_A);
        boolean foundV1 = false, foundV2 = false;
        for (Vertex v : vertices) {
            if (v.getId().equals("v1")) {
                assertEquals("v1", v.getPropertyValue("prop1"));
                foundV1 = true;
            } else if (v.getId().equals("v2")) {
                assertEquals("v2", v.getPropertyValue("prop1"));
                foundV2 = true;
            } else {
                assertTrue("Unexpected vertex id: " + v.getId(), false);
            }
        }
        assertTrue("v1 not found", foundV1);
        assertTrue("v2 not found", foundV2);

        List<Vertex> verticesInOrder = graph.getVerticesInOrder(ids, AUTHORIZATIONS_A);
        assertEquals(2, verticesInOrder.size());
        assertEquals("v2", verticesInOrder.get(0).getId());
        assertEquals("v1", verticesInOrder.get(1).getId());
    }

    @Test
    public void testGetEdgesWithIds() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v3 = graph.addVertex("v3", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.prepareEdge("e1", v1, v2, "", VISIBILITY_A)
                .setProperty("prop1", "e1", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareEdge("e1a", v1, v2, "", VISIBILITY_A)
                .setProperty("prop1", "e1a", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareEdge("e2", v1, v3, "", VISIBILITY_A)
                .setProperty("prop1", "e2", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareEdge("e3", v2, v3, "", VISIBILITY_A)
                .setProperty("prop1", "e3", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        List<String> ids = new ArrayList<>();
        ids.add("e1");
        ids.add("e2");
        Iterable<Edge> edges = graph.getEdges(ids, AUTHORIZATIONS_A);
        boolean foundE1 = false, foundE2 = false;
        for (Edge e : edges) {
            if (e.getId().equals("e1")) {
                assertEquals("e1", e.getPropertyValue("prop1"));
                foundE1 = true;
            } else if (e.getId().equals("e2")) {
                assertEquals("e2", e.getPropertyValue("prop1"));
                foundE2 = true;
            } else {
                assertTrue("Unexpected vertex id: " + e.getId(), false);
            }
        }
        assertTrue("e1 not found", foundE1);
        assertTrue("e2 not found", foundE2);
    }

    @Test
    public void testRemoveVertex() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);

        Assert.assertEquals(1, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));

        graph.removeVertex(v1, AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
    }

    @Test
    public void testMarkVertexHidden() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("v1tov2", v1, v2, "test", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.flush();

        List<String> vertexIdList = new ArrayList<>();
        vertexIdList.add("v1");
        vertexIdList.add("v2");
        vertexIdList.add("bad"); // add "bad" to the end of the list to test ordering of results
        Map<String, Boolean> verticesExist = graph.doVerticesExist(vertexIdList, AUTHORIZATIONS_A);
        assertEquals(3, vertexIdList.size());
        assertTrue("v1 exist", verticesExist.get("v1"));
        assertTrue("v2 exist", verticesExist.get("v2"));
        assertFalse("bad exist", verticesExist.get("bad"));

        assertTrue("v1 exists (auth A)", graph.doesVertexExist("v1", AUTHORIZATIONS_A));
        assertFalse("v1 exists (auth B)", graph.doesVertexExist("v1", AUTHORIZATIONS_B));
        assertTrue("v1 exists (auth A&B)", graph.doesVertexExist("v1", AUTHORIZATIONS_A_AND_B));
        Assert.assertEquals(2, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));

        graph.markVertexHidden(v1, VISIBILITY_A_AND_B, AUTHORIZATIONS_A);
        graph.flush();

        assertTrue("v1 exists (auth A)", graph.doesVertexExist("v1", AUTHORIZATIONS_A));
        assertFalse("v1 exists (auth B)", graph.doesVertexExist("v1", AUTHORIZATIONS_B));
        assertFalse("v1 exists (auth A&B)", graph.doesVertexExist("v1", AUTHORIZATIONS_A_AND_B));
        Assert.assertEquals(1, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(2, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
        Assert.assertEquals(0, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));

        graph.markVertexHidden(v1, VISIBILITY_A, AUTHORIZATIONS_A);
        graph.flush();

        assertFalse("v1 exists (auth A)", graph.doesVertexExist("v1", AUTHORIZATIONS_A));
        assertFalse("v1 exists (auth B)", graph.doesVertexExist("v1", AUTHORIZATIONS_B));
        assertFalse("v1 exists (auth A&B)", graph.doesVertexExist("v1", AUTHORIZATIONS_A_AND_B));
        Assert.assertEquals(1, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
        Assert.assertEquals(0, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_B)));
        Assert.assertEquals(0, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));
        assertNull("found v1 but shouldn't have", graph.getVertex("v1", FetchHint.ALL, AUTHORIZATIONS_A));
        Vertex v1Hidden = graph.getVertex("v1", FetchHint.ALL_INCLUDING_HIDDEN, AUTHORIZATIONS_A);
        assertNotNull("did not find v1 but should have", v1Hidden);
        assertTrue("v1 should be hidden", v1Hidden.isHidden(AUTHORIZATIONS_A));

        graph.markVertexVisible(v1, VISIBILITY_A, AUTHORIZATIONS_A);
        graph.flush();

        assertTrue("v1 exists (auth A)", graph.doesVertexExist("v1", AUTHORIZATIONS_A));
        assertFalse("v1 exists (auth B)", graph.doesVertexExist("v1", AUTHORIZATIONS_B));
        assertFalse("v1 exists (auth A&B)", graph.doesVertexExist("v1", AUTHORIZATIONS_A_AND_B));
        Assert.assertEquals(1, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(2, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
        Assert.assertEquals(0, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));

        graph.markVertexVisible(v1, VISIBILITY_A_AND_B, AUTHORIZATIONS_A);
        graph.flush();

        assertTrue("v1 exists (auth A)", graph.doesVertexExist("v1", AUTHORIZATIONS_A));
        assertFalse("v1 exists (auth B)", graph.doesVertexExist("v1", AUTHORIZATIONS_B));
        assertTrue("v1 exists (auth A&B)", graph.doesVertexExist("v1", AUTHORIZATIONS_A_AND_B));
        Assert.assertEquals(2, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));
    }

    @Test
    public void testMarkEdgeHidden() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v3 = graph.addVertex("v3", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge e1 = graph.addEdge("v1tov2", v1, v2, "test", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("v2tov3", v2, v3, "test", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.flush();

        List<String> edgeIdList = new ArrayList<>();
        edgeIdList.add("v1tov2");
        edgeIdList.add("v2tov3");
        edgeIdList.add("bad");
        Map<String, Boolean> edgesExist = graph.doEdgesExist(edgeIdList, AUTHORIZATIONS_A);
        assertEquals(3, edgeIdList.size());
        assertTrue("v1tov2 exist", edgesExist.get("v1tov2"));
        assertTrue("v2tov3 exist", edgesExist.get("v2tov3"));
        assertFalse("bad exist", edgesExist.get("bad"));

        assertTrue("v1tov2 exists (auth A)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_A));
        assertFalse("v1tov2 exists (auth B)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_B));
        assertTrue("v1tov2 exists (auth A&B)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_A_AND_B));
        Assert.assertEquals(3, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
        Assert.assertEquals(2, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A).getEdges(Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(graph.findPaths("v1", "v3", 2, AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.findPaths("v1", "v3", 10, AUTHORIZATIONS_A_AND_B)));

        graph.markEdgeHidden(e1, VISIBILITY_A_AND_B, AUTHORIZATIONS_A);
        graph.flush();

        assertTrue("v1tov2 exists (auth A)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_A));
        assertFalse("v1tov2 exists (auth B)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_B));
        assertFalse("v1tov2 exists (auth A&B)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_A_AND_B));
        Assert.assertEquals(2, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));
        Assert.assertEquals(0, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A).getEdges(Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(0, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).getEdges(Direction.BOTH, AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.getVertex("v1", FetchHint.ALL_INCLUDING_HIDDEN, AUTHORIZATIONS_A_AND_B).getEdges(Direction.BOTH, FetchHint.ALL_INCLUDING_HIDDEN, AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(0, IterableUtils.count(graph.findPaths("v1", "v3", 2, AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(0, IterableUtils.count(graph.findPaths("v1", "v3", 10, AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.findPaths("v1", "v3", 10, AUTHORIZATIONS_A)));
        assertNull("found e1 but shouldn't have", graph.getEdge("v1tov2", FetchHint.ALL, AUTHORIZATIONS_A_AND_B));
        Edge e1Hidden = graph.getEdge("v1tov2", FetchHint.ALL_INCLUDING_HIDDEN, AUTHORIZATIONS_A_AND_B);
        assertNotNull("did not find e1 but should have", e1Hidden);
        assertTrue("e1 should be hidden", e1Hidden.isHidden(AUTHORIZATIONS_A_AND_B));

        graph.markEdgeVisible(e1, VISIBILITY_A_AND_B, AUTHORIZATIONS_A);
        graph.flush();

        assertTrue("v1tov2 exists (auth A)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_A));
        assertFalse("v1tov2 exists (auth B)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_B));
        assertTrue("v1tov2 exists (auth A&B)", graph.doesEdgeExist("v1tov2", AUTHORIZATIONS_A_AND_B));
        Assert.assertEquals(3, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
        Assert.assertEquals(2, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A).getEdges(Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(graph.findPaths("v1", "v3", 2, AUTHORIZATIONS_A_AND_B)));
        Assert.assertEquals(1, IterableUtils.count(graph.findPaths("v1", "v3", 10, AUTHORIZATIONS_A_AND_B)));
    }

    @Test
    public void testMarkPropertyHidden() {
        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_A)
                .addPropertyValue("key1", "prop1", "value1", VISIBILITY_A)
                .addPropertyValue("key1", "prop1", "value1", VISIBILITY_B)
                .addPropertyValue("key2", "prop1", "value1", VISIBILITY_A)
                .save(AUTHORIZATIONS_A);

        Assert.assertEquals(3, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).getProperties("prop1")));

        v1.markPropertyHidden("key1", "prop1", VISIBILITY_A, VISIBILITY_A_AND_B, AUTHORIZATIONS_A_AND_B);

        List<Property> properties = toList(graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).getProperties("prop1"));
        Assert.assertEquals(2, IterableUtils.count(properties));
        boolean foundProp1Key2 = false;
        boolean foundProp1Key1VisB = false;
        for (Property property : properties) {
            if (property.getName().equals("prop1")) {
                if (property.getKey().equals("key2")) {
                    foundProp1Key2 = true;
                } else if (property.getKey().equals("key1")) {
                    if (property.getVisibility().equals(VISIBILITY_B)) {
                        foundProp1Key1VisB = true;
                    } else {
                        throw new RuntimeException("Unexpected visibility " + property.getVisibility());
                    }
                } else {
                    throw new RuntimeException("Unexpected property key " + property.getKey());
                }
            } else {
                throw new RuntimeException("Unexpected property name " + property.getName());
            }
        }
        assertTrue("Prop1Key2 not found", foundProp1Key2);
        assertTrue("Prop1Key1VisB not found", foundProp1Key1VisB);

        List<Property> hiddenProperties = toList(graph.getVertex("v1", FetchHint.ALL_INCLUDING_HIDDEN, AUTHORIZATIONS_A_AND_B).getProperties());
        assertEquals(3, hiddenProperties.size());
        boolean foundProp1Key1VisA = false;
        foundProp1Key2 = false;
        foundProp1Key1VisB = false;
        for (Property property : hiddenProperties) {
            if (property.getName().equals("prop1")) {
                if (property.getKey().equals("key2")) {
                    foundProp1Key2 = true;
                    assertFalse("should not be hidden", property.isHidden(AUTHORIZATIONS_A_AND_B));
                } else if (property.getKey().equals("key1")) {
                    if (property.getVisibility().equals(VISIBILITY_A)) {
                        foundProp1Key1VisA = true;
                        assertFalse("should not be hidden", property.isHidden(AUTHORIZATIONS_A));
                        assertTrue("should be hidden", property.isHidden(AUTHORIZATIONS_A_AND_B));
                    } else if (property.getVisibility().equals(VISIBILITY_B)) {
                        foundProp1Key1VisB = true;
                        assertFalse("should not be hidden", property.isHidden(AUTHORIZATIONS_A_AND_B));
                    } else {
                        throw new RuntimeException("Unexpected visibility " + property.getVisibility());
                    }
                } else {
                    throw new RuntimeException("Unexpected property key " + property.getKey());
                }
            } else {
                throw new RuntimeException("Unexpected property name " + property.getName());
            }
        }
        assertTrue("Prop1Key2 not found", foundProp1Key2);
        assertTrue("Prop1Key1VisB not found", foundProp1Key1VisB);
        assertTrue("Prop1Key1VisA not found", foundProp1Key1VisA);

        v1.markPropertyVisible("key1", "prop1", VISIBILITY_A, VISIBILITY_A_AND_B, AUTHORIZATIONS_A_AND_B);

        Assert.assertEquals(3, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).getProperties("prop1")));
    }

    /**
     * This tests simulates two workspaces w1 (via A) and w1 (vis B).
     * Both w1 and w2 has e1 on it.
     * e1 is linked to e2.
     * <p/>
     * What happens if w1 (vis A) marks e1 hidden, then deletes itself?
     */
    @Test
    public void testMarkVertexHiddenAndDeleteEdges() {
        Vertex w1 = graph.addVertex("w1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex w2 = graph.addVertex("w2", VISIBILITY_B, AUTHORIZATIONS_B);
        Vertex e1 = graph.addVertex("e1", VISIBILITY_EMPTY, AUTHORIZATIONS_A);
        Vertex e2 = graph.addVertex("e2", VISIBILITY_EMPTY, AUTHORIZATIONS_A);
        graph.addEdge("w1-e1", w1, e1, "test", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("w2-e1", w2, e1, "test", VISIBILITY_B, AUTHORIZATIONS_B);
        graph.addEdge("e1-e2", e1, e2, "test", VISIBILITY_EMPTY, AUTHORIZATIONS_A);
        graph.flush();

        e1 = graph.getVertex("e1", AUTHORIZATIONS_EMPTY);
        graph.markVertexHidden(e1, VISIBILITY_A, AUTHORIZATIONS_EMPTY);
        graph.flush();

        w1 = graph.getVertex("w1", AUTHORIZATIONS_A);
        graph.removeVertex(w1, AUTHORIZATIONS_A);
        graph.flush();

        Assert.assertEquals(1, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));
        assertEquals("e2", toList(graph.getVertices(AUTHORIZATIONS_A)).get(0).getId());

        Assert.assertEquals(3, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_B)));
        boolean foundW2 = false;
        boolean foundE1 = false;
        boolean foundE2 = false;
        for (Vertex v : graph.getVertices(AUTHORIZATIONS_B)) {
            if (v.getId().equals("w2")) {
                foundW2 = true;
            } else if (v.getId().equals("e1")) {
                foundE1 = true;
            } else if (v.getId().equals("e2")) {
                foundE2 = true;
            } else {
                throw new VertexiumException("Unexpected id: " + v.getId());
            }
        }
        assertTrue("w2", foundW2);
        assertTrue("e1", foundE1);
        assertTrue("e2", foundE2);
    }

    @Test
    public void testRemoveVertexWithProperties() {
        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);
        Property prop1 = v1.getProperty("prop1");

        Assert.assertEquals(1, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A)));

        graph.removeVertex(v1, AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(graph.getVertices(AUTHORIZATIONS_A_AND_B)));

        graph.flush();
        assertEvents(
                new AddVertexEvent(graph, v1),
                new AddPropertyEvent(graph, v1, prop1),
                new RemoveVertexEvent(graph, v1)
        );
    }

    @Test
    public void testAddEdge() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge addedEdge = graph.addEdge("e1", v1, v2, "label1", VISIBILITY_A, AUTHORIZATIONS_A);
        assertNotNull(addedEdge);
        assertEquals("e1", addedEdge.getId());
        assertEquals("label1", addedEdge.getLabel());
        assertEquals("v1", addedEdge.getVertexId(Direction.OUT));
        assertEquals(v1, addedEdge.getVertex(Direction.OUT, AUTHORIZATIONS_A));
        assertEquals("v2", addedEdge.getVertexId(Direction.IN));
        assertEquals(v2, addedEdge.getVertex(Direction.IN, AUTHORIZATIONS_A));
        assertEquals(VISIBILITY_A, addedEdge.getVisibility());

        graph.getVertex("v1", FetchHint.NONE, AUTHORIZATIONS_A);
        graph.getVertex("v1", FetchHint.ALL, AUTHORIZATIONS_A);
        graph.getVertex("v1", EnumSet.of(FetchHint.PROPERTIES), AUTHORIZATIONS_A);
        graph.getVertex("v1", FetchHint.EDGE_REFS, AUTHORIZATIONS_A);
        graph.getVertex("v1", EnumSet.of(FetchHint.IN_EDGE_REFS), AUTHORIZATIONS_A);
        graph.getVertex("v1", EnumSet.of(FetchHint.OUT_EDGE_REFS), AUTHORIZATIONS_A);

        graph.getEdge("e1", FetchHint.NONE, AUTHORIZATIONS_A);
        graph.getEdge("e1", FetchHint.ALL, AUTHORIZATIONS_A);
        graph.getEdge("e1", EnumSet.of(FetchHint.PROPERTIES), AUTHORIZATIONS_A);

        Edge e = graph.getEdge("e1", AUTHORIZATIONS_B);
        assertNull(e);

        e = graph.getEdge("e1", AUTHORIZATIONS_A);
        assertNotNull(e);
        assertEquals("e1", e.getId());
        assertEquals("label1", e.getLabel());
        assertEquals("v1", e.getVertexId(Direction.OUT));
        assertEquals(v1, e.getVertex(Direction.OUT, AUTHORIZATIONS_A));
        assertEquals("v2", e.getVertexId(Direction.IN));
        assertEquals(v2, e.getVertex(Direction.IN, AUTHORIZATIONS_A));
        assertEquals(VISIBILITY_A, e.getVisibility());

        graph.flush();
        assertEvents(
                new AddVertexEvent(graph, v1),
                new AddVertexEvent(graph, v2),
                new AddEdgeEvent(graph, addedEdge)
        );
    }

    @Test
    public void testGetEdge() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("e1to2label1", v1, v2, "label1", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("e1to2label2", v1, v2, "label2", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("e2to1", v2.getId(), v1.getId(), "label1", VISIBILITY_A, AUTHORIZATIONS_A);

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);

        Assert.assertEquals(3, IterableUtils.count(v1.getEdges(Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(2, IterableUtils.count(v1.getEdges(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(Direction.IN, AUTHORIZATIONS_A)));
        Assert.assertEquals(3, IterableUtils.count(v1.getEdges(v2, Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(2, IterableUtils.count(v1.getEdges(v2, Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(v2, Direction.IN, AUTHORIZATIONS_A)));
        Assert.assertEquals(2, IterableUtils.count(v1.getEdges(v2, Direction.BOTH, "label1", AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(v2, Direction.OUT, "label1", AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(v2, Direction.IN, "label1", AUTHORIZATIONS_A)));
        Assert.assertEquals(3, IterableUtils.count(v1.getEdges(v2, Direction.BOTH, new String[]{"label1", "label2"}, AUTHORIZATIONS_A)));
        Assert.assertEquals(2, IterableUtils.count(v1.getEdges(v2, Direction.OUT, new String[]{"label1", "label2"}, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(v2, Direction.IN, new String[]{"label1", "label2"}, AUTHORIZATIONS_A)));

        Assert.assertArrayEquals(new String[]{"label1", "label2"}, IterableUtils.toArray(v1.getEdgeLabels(Direction.OUT, AUTHORIZATIONS_A), String.class));
        Assert.assertArrayEquals(new String[]{"label1"}, IterableUtils.toArray(v1.getEdgeLabels(Direction.IN, AUTHORIZATIONS_A), String.class));
        Assert.assertArrayEquals(new String[]{"label1", "label2"}, IterableUtils.toArray(v1.getEdgeLabels(Direction.BOTH, AUTHORIZATIONS_A), String.class));
    }

    @Test
    public void testAddEdgeWithProperties() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge addedEdge = graph.prepareEdge("e1", v1, v2, "label1", VISIBILITY_A)
                .setProperty("propA", "valueA", VISIBILITY_A)
                .setProperty("propB", "valueB", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        Edge e = graph.getEdge("e1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(e.getProperties()));
        assertEquals("valueA", e.getPropertyValues("propA").iterator().next());
        Assert.assertEquals(0, IterableUtils.count(e.getPropertyValues("propB")));

        e = graph.getEdge("e1", AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(2, IterableUtils.count(e.getProperties()));
        assertEquals("valueA", e.getPropertyValues("propA").iterator().next());
        assertEquals("valueB", e.getPropertyValues("propB").iterator().next());
        assertEquals("valueA", e.getPropertyValue("propA"));
        assertEquals("valueB", e.getPropertyValue("propB"));

        graph.flush();
        assertEvents(
                new AddVertexEvent(graph, v1),
                new AddVertexEvent(graph, v2),
                new AddEdgeEvent(graph, addedEdge),
                new AddPropertyEvent(graph, addedEdge, addedEdge.getProperty("propA")),
                new AddPropertyEvent(graph, addedEdge, addedEdge.getProperty("propB"))
        );
    }

    @Test
    public void testAlterEdgeLabel() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.prepareEdge("e1", v1, v2, "label1", VISIBILITY_A)
                .setProperty("propA", "valueA", VISIBILITY_A)
                .save(AUTHORIZATIONS_A);

        Edge e = graph.getEdge("e1", AUTHORIZATIONS_A);
        assertEquals("label1", e.getLabel());
        Assert.assertEquals(1, IterableUtils.count(e.getProperties()));
        assertEquals("valueA", e.getPropertyValues("propA").iterator().next());
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals("label1", IterableUtils.single(v1.getEdgeLabels(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v2.getEdges(Direction.IN, AUTHORIZATIONS_A)));
        Assert.assertEquals("label1", IterableUtils.single(v2.getEdgeLabels(Direction.IN, AUTHORIZATIONS_A)));

        e.prepareMutation()
                .alterEdgeLabel("label2")
                .save(AUTHORIZATIONS_A);
        e = graph.getEdge("e1", AUTHORIZATIONS_A);
        assertEquals("label2", e.getLabel());
        Assert.assertEquals(1, IterableUtils.count(e.getProperties()));
        assertEquals("valueA", e.getPropertyValues("propA").iterator().next());
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals("label2", IterableUtils.single(v1.getEdgeLabels(Direction.OUT, AUTHORIZATIONS_A)));
        v2 = graph.getVertex("v2", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v2.getEdges(Direction.IN, AUTHORIZATIONS_A)));
        Assert.assertEquals("label2", IterableUtils.single(v2.getEdgeLabels(Direction.IN, AUTHORIZATIONS_A)));

        graph.prepareEdge(e.getId(), e.getVertexId(Direction.OUT), e.getVertexId(Direction.IN), e.getLabel(), e.getVisibility())
                .alterEdgeLabel("label3")
                .save(AUTHORIZATIONS_A);
        e = graph.getEdge("e1", AUTHORIZATIONS_A);
        assertEquals("label3", e.getLabel());
        Assert.assertEquals(1, IterableUtils.count(e.getProperties()));
        assertEquals("valueA", e.getPropertyValues("propA").iterator().next());
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals("label3", IterableUtils.single(v1.getEdgeLabels(Direction.OUT, AUTHORIZATIONS_A)));
        v2 = graph.getVertex("v2", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v2.getEdges(Direction.IN, AUTHORIZATIONS_A)));
        Assert.assertEquals("label3", IterableUtils.single(v2.getEdgeLabels(Direction.IN, AUTHORIZATIONS_A)));
    }

    @Test
    public void testRemoveEdge() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge addedEdge = graph.addEdge("e1", v1, v2, "label1", VISIBILITY_A, AUTHORIZATIONS_A);

        Assert.assertEquals(1, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));

        try {
            graph.removeEdge("e1", AUTHORIZATIONS_B);
        } catch (IllegalArgumentException e) {
            // expected
        }
        Assert.assertEquals(1, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));

        graph.removeEdge("e1", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(graph.getEdges(AUTHORIZATIONS_A)));

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(v1.getVertices(Direction.BOTH, AUTHORIZATIONS_A)));
        v2 = graph.getVertex("v2", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(v2.getVertices(Direction.BOTH, AUTHORIZATIONS_A)));

        graph.flush();
        assertEvents(
                new AddVertexEvent(graph, v1),
                new AddVertexEvent(graph, v2),
                new AddEdgeEvent(graph, addedEdge),
                new RemoveEdgeEvent(graph, addedEdge)
        );
    }

    @Test
    public void testAddEdgeWithVisibility() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("e1", v1, v2, "edgeA", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("e2", v1, v2, "edgeB", VISIBILITY_B, AUTHORIZATIONS_B);

        Iterable<Edge> aEdges = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).getEdges(Direction.BOTH, AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(aEdges));
        Edge e1 = aEdges.iterator().next();
        assertNotNull(e1);
        assertEquals("edgeA", e1.getLabel());

        Iterable<Edge> bEdges = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).getEdges(Direction.BOTH, AUTHORIZATIONS_B);
        Assert.assertEquals(1, IterableUtils.count(bEdges));
        Edge e2 = bEdges.iterator().next();
        assertNotNull(e2);
        assertEquals("edgeB", e2.getLabel());

        Iterable<Edge> allEdges = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).getEdges(Direction.BOTH, AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(2, IterableUtils.count(allEdges));
    }

    @Test
    public void testGraphQuery() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("e1", v1, v2, "edgeA", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.flush();

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A).vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A).skip(1).vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A).limit(1).vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A).skip(1).limit(1).vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A).skip(2).vertices();
        Assert.assertEquals(0, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A).skip(1).limit(2).vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        Iterable<Edge> edges = graph.query(AUTHORIZATIONS_A).edges();
        Assert.assertEquals(1, IterableUtils.count(edges));
    }

    @Test
    public void testGraphQueryWithQueryString() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        v1.setProperty("description", "This is vertex 1 - dog.", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        v2.setProperty("description", "This is vertex 2 - cat.", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query("vertex", AUTHORIZATIONS_A).vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));

        vertices = graph.query("dog", AUTHORIZATIONS_A).vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query("dog", AUTHORIZATIONS_B).vertices();
        Assert.assertEquals(0, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryHas() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("text", "hello", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_A)
                .setProperty("birthDate", new DateOnly(1989, 1, 5), VISIBILITY_A)
                .setProperty("lastAccessed", createDate(2014, 2, 24, 13, 0, 5), VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("text", "world", VISIBILITY_A)
                .setProperty("age", 30, VISIBILITY_A)
                .setProperty("birthDate", new DateOnly(1984, 1, 5), VISIBILITY_A)
                .setProperty("lastAccessed", createDate(2014, 2, 25, 13, 0, 5), VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.EQUAL, 25)
                .has("birthDate", Compare.EQUAL, createDate(1989, 1, 5))
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query("hello", AUTHORIZATIONS_A)
                .has("age", Compare.EQUAL, 25)
                .has("birthDate", Compare.EQUAL, createDate(1989, 1, 5))
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("birthDate", Compare.EQUAL, createDate(1989, 1, 5))
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("lastAccessed", Compare.EQUAL, createDate(2014, 2, 24, 13, 0, 5))
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.GREATER_THAN_EQUAL, 25)
                .vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.IN, new Integer[]{25})
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.IN, new Integer[]{25, 30})
                .vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.GREATER_THAN, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.LESS_THAN, 26)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.LESS_THAN_EQUAL, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.NOT_EQUAL, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("lastAccessed", Compare.EQUAL, new DateOnly(2014, 2, 24))
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query("*", AUTHORIZATIONS_A)
                .has("age", Compare.IN, new Integer[]{25, 30})
                .vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));

        vertices = new CompositeGraphQuery(
                graph.query(AUTHORIZATIONS_A).has("age", 25),
                graph.query(AUTHORIZATIONS_A).has("age", 25),
                graph.query(AUTHORIZATIONS_A).has("age", 30)
        ).vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryVertexHasWithSecurity() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_B)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(0, IterableUtils.count(vertices)); // need auth A to see the v2 node itself

        vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryVertexHasWithSecurityGranularity() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A)
                .vertices();
        boolean hasAgeVisA = false;
        boolean hasAgeVisB = false;
        for (Vertex v : vertices) {
            Property prop = v.getProperty("age");
            if (prop == null) {
                continue;
            }
            if ((Integer) prop.getValue() == 25) {
                if (prop.getVisibility().equals(VISIBILITY_A)) {
                    hasAgeVisA = true;
                } else if (prop.getVisibility().equals(VISIBILITY_B)) {
                    hasAgeVisB = true;
                }
            }
        }
        if (graph.getSearchIndexSecurityGranularity() == SearchIndexSecurityGranularity.DOCUMENT) {
            Assert.assertEquals(1, IterableUtils.count(vertices));
            assertTrue("has a", hasAgeVisA);
            assertFalse("has b", hasAgeVisB);
        } else if (graph.getSearchIndexSecurityGranularity() == SearchIndexSecurityGranularity.PROPERTY) {
            Assert.assertEquals(2, IterableUtils.count(vertices));
            assertTrue("has a", hasAgeVisA);
            assertFalse("has b", hasAgeVisB);
        }

        vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryVertexHasWithSecurityComplexFormula() {
        graph.prepareVertex("v1", VISIBILITY_MIXED_CASE_a)
                .setProperty("age", 25, VISIBILITY_MIXED_CASE_a)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_MIXED_CASE_a_AND_B)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryVertexNoVisibility() {
        graph.prepareVertex("v1", VISIBILITY_EMPTY)
                .setProperty("text", "hello", VISIBILITY_EMPTY)
                .setProperty("age", 25, VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();

        Iterable<Vertex> vertices = graph.query("hello", AUTHORIZATIONS_A_AND_B)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query("hello", AUTHORIZATIONS_A_AND_B)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryVertexHasWithSecurityCantSeeVertex() {
        graph.prepareVertex("v1", VISIBILITY_B)
                .setProperty("age", 25, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(0, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryVertexHasWithSecurityCantSeeProperty() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(0, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryEdgeHasWithSecurity() {
        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_A).save(AUTHORIZATIONS_A_AND_B);
        Vertex v2 = graph.prepareVertex("v2", VISIBILITY_A).save(AUTHORIZATIONS_A_AND_B);
        Vertex v3 = graph.prepareVertex("v3", VISIBILITY_A).save(AUTHORIZATIONS_A_AND_B);

        graph.prepareEdge("e1", v1, v2, "edge", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareEdge("e2", v1, v3, "edge", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Edge> edges = graph.query(AUTHORIZATIONS_A)
                .has("age", Compare.EQUAL, 25)
                .edges();
        Assert.assertEquals(1, IterableUtils.count(edges));
    }

    @Test
    public void testGraphQueryUpdateVertex() throws NoSuchFieldException, IllegalAccessException {
        graph.prepareVertex("v1", VISIBILITY_EMPTY)
                .setProperty("age", 25, VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        Vertex v2 = graph.prepareVertex("v2", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        Vertex v3 = graph.prepareVertex("v3", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.addEdge("v2tov3", v2, v3, "", VISIBILITY_EMPTY, AUTHORIZATIONS_A_AND_B);
        graph.flush();

        boolean disableUpdateEdgeCountInSearchIndexSuccess = disableUpdateEdgeCountInSearchIndex(graph);

        graph.prepareVertex("v1", VISIBILITY_EMPTY)
                .setProperty("name", "Joe", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_EMPTY)
                .setProperty("name", "Bob", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v3", VISIBILITY_EMPTY)
                .setProperty("name", "Same", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();

        List<Vertex> allVertices = toList(graph.query(AUTHORIZATIONS_A_AND_B).vertices());
        Assert.assertEquals(3, IterableUtils.count(allVertices));
        if (disableUpdateEdgeCountInSearchIndexSuccess) {
            assertEquals(
                    "if edge indexing was disabled and updating vertices does not destroy the edge counts " +
                            "that were already in place 'v1' should be last since it has no edges",
                    "v1", allVertices.get(2).getId());
        }

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("age", Compare.EQUAL, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("name", Compare.EQUAL, "Joe")
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("age", Compare.EQUAL, 25)
                .has("name", Compare.EQUAL, "Joe")
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));
    }

    @Test
    public void testQueryWithVertexIds() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("age", 30, VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.prepareVertex("v3", VISIBILITY_A)
                .setProperty("age", 35, VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.flush();

        List<Vertex> vertices = toList(graph.query(new String[]{"v1", "v2"}, AUTHORIZATIONS_A)
                .has("age", Compare.GREATER_THAN, 27)
                .vertices());
        Assert.assertEquals(1, vertices.size());
        Assert.assertEquals("v2", vertices.get(0).getId());

        vertices = toList(graph.query(new String[]{"v1", "v2", "v3"}, AUTHORIZATIONS_A)
                .has("age", Compare.GREATER_THAN, 27)
                .vertices());
        Assert.assertEquals(2, vertices.size());
        List<String> vertexIds = toList(new ConvertingIterable<Vertex, String>(vertices) {
            @Override
            protected String convert(Vertex o) {
                return o.getId();
            }
        });
        Assert.assertTrue("v2 not found", vertexIds.contains("v2"));
        Assert.assertTrue("v3 not found", vertexIds.contains("v3"));
    }

    @Test
    public void testDisableEdgeIndexing() throws NoSuchFieldException, IllegalAccessException {
        if (!disableEdgeIndexing(graph)) {
            LOGGER.info("skipping " + SearchIndex.class.getSimpleName() + " doesn't support disabling index");
            return;
        }

        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_A).save(AUTHORIZATIONS_A_AND_B);
        Vertex v2 = graph.prepareVertex("v2", VISIBILITY_A).save(AUTHORIZATIONS_A_AND_B);

        graph.prepareEdge("e1", v1, v2, "edge", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();

        Iterable<Edge> edges = graph.query(AUTHORIZATIONS_A)
                .has("prop1", "value1")
                .edges();
        Assert.assertEquals(0, IterableUtils.count(edges));
    }

    @Test
    public void testGraphQueryHasWithSpaces() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("name", "Joe Ferner", VISIBILITY_A)
                .setProperty("propWithHyphen", "hyphen-word", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("name", "Joe Smith", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query("Ferner", AUTHORIZATIONS_A)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query("joe", AUTHORIZATIONS_A)
                .vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));

        if (!isUsingDefaultQuery(graph)) {
            vertices = graph.query("joe AND ferner", AUTHORIZATIONS_A)
                    .vertices();
            Assert.assertEquals(1, IterableUtils.count(vertices));
        }

        if (!isUsingDefaultQuery(graph)) {
            vertices = graph.query("joe smith", AUTHORIZATIONS_A)
                    .vertices();
            List<Vertex> verticesList = toList(vertices);
            assertEquals(2, verticesList.size());
            boolean foundV1 = false;
            boolean foundV2 = false;
            for (Vertex v : verticesList) {
                if (v.getId().equals("v1")) {
                    foundV1 = true;
                } else if (v.getId().equals("v2")) {
                    foundV2 = true;
                } else {
                    throw new RuntimeException("Invalid vertex id: " + v.getId());
                }
            }
            assertTrue(foundV1);
            assertTrue(foundV2);
        }

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("name", TextPredicate.CONTAINS, "Ferner")
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("name", TextPredicate.CONTAINS, "Joe")
                .has("name", TextPredicate.CONTAINS, "Ferner")
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("name", TextPredicate.CONTAINS, "Joe Ferner")
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .has("propWithHyphen", TextPredicate.CONTAINS, "hyphen-word")
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));
    }

    @Test
    public void testGraphQueryHasWithSpacesAndFieldedQueryString() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("name", "Joe Ferner", VISIBILITY_A)
                .setProperty("propWithHyphen", "hyphen-word", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("name", "Joe Smith", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        if (!isUsingDefaultQuery(graph)) {
            Iterable<Vertex> vertices = graph.query("name:\"joe ferner\"", AUTHORIZATIONS_A)
                    .vertices();
            Assert.assertEquals(1, IterableUtils.count(vertices));
        }
    }

    protected boolean isUsingDefaultQuery(Graph graph) {
        return graph.query(AUTHORIZATIONS_A) instanceof DefaultGraphQuery;
    }

    @Test
    public void testGraphQueryGeoPoint() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("location", new GeoPoint(38.9186, -77.2297, "Reston, VA"), VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("location", new GeoPoint(38.9544, -77.3464, "Reston, VA"), VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        List<Vertex> vertices = toList(graph.query(AUTHORIZATIONS_A)
                .has("location", GeoCompare.WITHIN, new GeoCircle(38.9186, -77.2297, 1))
                .vertices());
        Assert.assertEquals(1, IterableUtils.count(vertices));
        GeoPoint geoPoint = (GeoPoint) vertices.get(0).getPropertyValue("location");
        assertEquals(38.9186, geoPoint.getLatitude(), 0.001);
        assertEquals(-77.2297, geoPoint.getLongitude(), 0.001);
        assertEquals("Reston, VA", geoPoint.getDescription());

        vertices = toList(graph.query(AUTHORIZATIONS_A)
                .has("location", GeoCompare.WITHIN, new GeoCircle(38.9186, -77.2297, 25))
                .vertices());
        Assert.assertEquals(2, IterableUtils.count(vertices));

        vertices = toList(graph.query(AUTHORIZATIONS_A)
                .has("location", TextPredicate.CONTAINS, "Reston")
                .vertices());
        Assert.assertEquals(2, IterableUtils.count(vertices));
    }

    private Date createDate(int year, int month, int day) {
        return new GregorianCalendar(year, month, day).getTime();
    }

    private Date createDate(int year, int month, int day, int hour, int min, int sec) {
        return new GregorianCalendar(year, month, day, hour, min, sec).getTime();
    }

    @Test
    public void testGraphQueryRange() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("age", 30, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A)
                .range("age", 25, 25)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .range("age", 20, 29)
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));

        vertices = graph.query(AUTHORIZATIONS_A)
                .range("age", 25, 30)
                .vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));
    }

    @Test
    public void testVertexQuery() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        v1.setProperty("prop1", "value1", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);

        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        v2.setProperty("prop1", "value2", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);

        Vertex v3 = graph.addVertex("v3", VISIBILITY_A, AUTHORIZATIONS_A);
        v3.setProperty("prop1", "value3", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);

        Edge ev1v2 = graph.addEdge("e v1->v2", v1, v2, "edgeA", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge ev1v3 = graph.addEdge("e v1->v3", v1, v3, "edgeA", VISIBILITY_A, AUTHORIZATIONS_A);

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Iterable<Vertex> vertices = v1.query(AUTHORIZATIONS_A).vertices();
        Assert.assertEquals(2, IterableUtils.count(vertices));
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(v2, vertices);
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(v3, vertices);

        vertices = v1.query(AUTHORIZATIONS_A)
                .has("prop1", "value2")
                .vertices();
        Assert.assertEquals(1, IterableUtils.count(vertices));
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(v2, vertices);

        Iterable<Edge> edges = v1.query(AUTHORIZATIONS_A).edges();
        Assert.assertEquals(2, IterableUtils.count(edges));
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(ev1v2, edges);
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(ev1v3, edges);

        edges = v1.query(AUTHORIZATIONS_A).edges(Direction.OUT);
        Assert.assertEquals(2, IterableUtils.count(edges));
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(ev1v2, edges);
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(ev1v3, edges);
    }

    @Test
    public void testFindPaths() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v3 = graph.addVertex("v3", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v4 = graph.addVertex("v4", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge(v1, v2, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v1 -> v2
        graph.addEdge(v2, v4, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v2 -> v4
        graph.addEdge(v1, v3, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v1 -> v3
        graph.addEdge(v3, v4, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v3 -> v4

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        v4 = graph.getVertex("v4", AUTHORIZATIONS_A);
        List<Path> paths = toList(graph.findPaths(v1, v4, 2, AUTHORIZATIONS_A));
        // v1 -> v2 -> v4
        // v1 -> v3 -> v4
        assertEquals(2, paths.size());
        boolean found2 = false;
        boolean found3 = false;
        for (Path path : paths) {
            assertEquals(3, path.length());
            int i = 0;
            for (String id : path) {
                if (i == 0) {
                    assertEquals(id, v1.getId());
                } else if (i == 1) {
                    if (v2.getId().equals(id)) {
                        found2 = true;
                    } else if (v3.getId().equals(id)) {
                        found3 = true;
                    } else {
                        fail("center of path is neither v2 or v3 but found " + id);
                    }
                } else if (i == 2) {
                    assertEquals(id, v4.getId());
                }
                i++;
            }
        }
        assertTrue("v2 not found in path", found2);
        assertTrue("v3 not found in path", found3);

        v4 = graph.getVertex("v4", AUTHORIZATIONS_A);
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        paths = toList(graph.findPaths(v4, v1, 2, AUTHORIZATIONS_A));
        // v4 -> v2 -> v1
        // v4 -> v3 -> v1
        assertEquals(2, paths.size());
        found2 = false;
        found3 = false;
        for (Path path : paths) {
            assertEquals(3, path.length());
            int i = 0;
            for (String id : path) {
                if (i == 0) {
                    assertEquals(id, v4.getId());
                } else if (i == 1) {
                    if (v2.getId().equals(id)) {
                        found2 = true;
                    } else if (v3.getId().equals(id)) {
                        found3 = true;
                    } else {
                        fail("center of path is neither v2 or v3 but found " + id);
                    }
                } else if (i == 2) {
                    assertEquals(id, v1.getId());
                }
                i++;
            }
        }
        assertTrue("v2 not found in path", found2);
        assertTrue("v3 not found in path", found3);
    }

    @Test
    public void testFindPathsMultiplePaths() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v3 = graph.addVertex("v3", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v4 = graph.addVertex("v4", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v5 = graph.addVertex("v5", VISIBILITY_A, AUTHORIZATIONS_A);

        graph.addEdge(v1, v4, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v1 -> v4
        graph.addEdge(v1, v3, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v1 -> v3
        graph.addEdge(v3, v4, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v3 -> v4
        graph.addEdge(v2, v3, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v2 -> v3
        graph.addEdge(v4, v2, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v4 -> v2
        graph.addEdge(v2, v5, "knows", VISIBILITY_A, AUTHORIZATIONS_A); // v2 -> v5

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        v2 = graph.getVertex("v2", AUTHORIZATIONS_A);
        v5 = graph.getVertex("v5", AUTHORIZATIONS_A);

        List<Path> paths = toList(graph.findPaths(v1, v2, 2, AUTHORIZATIONS_A));
        // v1 -> v4 -> v2
        // v1 -> v3 -> v2
        assertEquals(2, paths.size());
        boolean found3 = false;
        boolean found4 = false;
        for (Path path : paths) {
            assertEquals(3, path.length());
            int i = 0;
            for (String id : path) {
                if (i == 0) {
                    assertEquals(id, v1.getId());
                } else if (i == 1) {
                    if (v3.getId().equals(id)) {
                        found3 = true;
                    } else if (v4.getId().equals(id)) {
                        found4 = true;
                    } else {
                        fail("center of path is neither v2 or v3 but found " + id);
                    }
                } else if (i == 2) {
                    assertEquals(id, v2.getId());
                }
                i++;
            }
        }
        assertTrue("v3 not found in path", found3);
        assertTrue("v4 not found in path", found4);

        paths = toList(graph.findPaths(v1, v2, 3, AUTHORIZATIONS_A));
        // v1 -> v4 -> v2
        // v1 -> v3 -> v2
        // v1 -> v3 -> v4 -> v2
        // v1 -> v4 -> v3 -> v2
        assertEquals(4, paths.size());
        found3 = false;
        found4 = false;
        for (Path path : paths) {
            if (path.length() == 3) {
                int i = 0;
                for (String id : path) {
                    if (i == 0) {
                        assertEquals(id, v1.getId());
                    } else if (i == 1) {
                        if (v3.getId().equals(id)) {
                            found3 = true;
                        } else if (v4.getId().equals(id)) {
                            found4 = true;
                        } else {
                            fail("center of path is neither v2 or v3 but found " + id);
                        }
                    } else if (i == 2) {
                        assertEquals(id, v2.getId());
                    }
                    i++;
                }
            } else if (path.length() == 4) {
                assertTrue(true);
            } else {
                fail("Invalid path length " + path.length());
            }
        }
        assertTrue("v3 not found in path", found3);
        assertTrue("v4 not found in path", found4);

        paths = toList(graph.findPaths(v1, v5, 2, AUTHORIZATIONS_A));
        assertEquals(0, paths.size());

        paths = toList(graph.findPaths(v1, v5, 3, AUTHORIZATIONS_A));
        // v1 -> v4 -> v2 -> v5
        // v1 -> v3 -> v2 -> v5
        assertEquals(2, paths.size());
        found3 = false;
        found4 = false;
        for (Path path : paths) {
            assertEquals(4, path.length());
            int i = 0;
            for (String id : path) {
                if (i == 0) {
                    assertEquals(id, v1.getId());
                } else if (i == 1) {
                    if (v3.getId().equals(id)) {
                        found3 = true;
                    } else if (v4.getId().equals(id)) {
                        found4 = true;
                    } else {
                        fail("center of path is neither v2 or v3 but found " + id);
                    }
                } else if (i == 2) {
                    assertEquals(id, v2.getId());
                } else if (i == 3) {
                    assertEquals(id, v5.getId());
                }
                i++;
            }
        }
        assertTrue("v3 not found in path", found3);
        assertTrue("v4 not found in path", found4);
    }

    @Test
    public void testGetVerticesFromVertex() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v3 = graph.addVertex("v3", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v4 = graph.addVertex("v4", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge(v1, v2, "knows", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge(v1, v3, "knows", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge(v1, v4, "knows", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge(v2, v3, "knows", VISIBILITY_A, AUTHORIZATIONS_A);

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(3, IterableUtils.count(v1.getVertices(Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(3, IterableUtils.count(v1.getVertices(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals(0, IterableUtils.count(v1.getVertices(Direction.IN, AUTHORIZATIONS_A)));

        v2 = graph.getVertex("v2", AUTHORIZATIONS_A);
        Assert.assertEquals(2, IterableUtils.count(v2.getVertices(Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v2.getVertices(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v2.getVertices(Direction.IN, AUTHORIZATIONS_A)));

        v3 = graph.getVertex("v3", AUTHORIZATIONS_A);
        Assert.assertEquals(2, IterableUtils.count(v3.getVertices(Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(0, IterableUtils.count(v3.getVertices(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals(2, IterableUtils.count(v3.getVertices(Direction.IN, AUTHORIZATIONS_A)));

        v4 = graph.getVertex("v4", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v4.getVertices(Direction.BOTH, AUTHORIZATIONS_A)));
        Assert.assertEquals(0, IterableUtils.count(v4.getVertices(Direction.OUT, AUTHORIZATIONS_A)));
        Assert.assertEquals(1, IterableUtils.count(v4.getVertices(Direction.IN, AUTHORIZATIONS_A)));
    }

    @Test
    public void testBlankVisibilityString() {
        Vertex v = graph.addVertex("v1", VISIBILITY_EMPTY, AUTHORIZATIONS_EMPTY);
        assertNotNull(v);
        assertEquals("v1", v.getId());

        v = graph.getVertex("v1", AUTHORIZATIONS_EMPTY);
        assertNotNull(v);
        assertEquals("v1", v.getId());
        assertEquals(VISIBILITY_EMPTY, v.getVisibility());
    }

    @Test
    public void testElementMutationDoesntChangeObjectUntilSave() {
        Vertex v = graph.addVertex("v1", VISIBILITY_EMPTY, AUTHORIZATIONS_EMPTY);
        v.setProperty("prop1", "value1", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);

        ElementMutation<Vertex> m = v.prepareMutation()
                .setProperty("prop1", "value2", VISIBILITY_A)
                .setProperty("prop2", "value2", VISIBILITY_A);
        Assert.assertEquals(1, IterableUtils.count(v.getProperties()));
        assertEquals("value1", v.getPropertyValue("prop1"));

        m.save(AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(2, IterableUtils.count(v.getProperties()));
        assertEquals("value2", v.getPropertyValue("prop1"));
        assertEquals("value2", v.getPropertyValue("prop2"));
    }

    @Test
    public void testFindRelatedEdges() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v3 = graph.addVertex("v3", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v4 = graph.addVertex("v4", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge ev1v2 = graph.addEdge("e v1->v2", v1, v2, "", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge ev1v3 = graph.addEdge("e v1->v3", v1, v3, "", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge ev2v3 = graph.addEdge("e v2->v3", v2, v3, "", VISIBILITY_A, AUTHORIZATIONS_A);
        Edge ev3v1 = graph.addEdge("e v3->v1", v3, v1, "", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.addEdge("e v3->v4", v3, v4, "", VISIBILITY_A, AUTHORIZATIONS_A);

        List<String> vertexIds = new ArrayList<>();
        vertexIds.add("v1");
        vertexIds.add("v2");
        vertexIds.add("v3");
        Iterable<String> edgeIds = toList(graph.findRelatedEdges(vertexIds, AUTHORIZATIONS_A));
        Assert.assertEquals(4, IterableUtils.count(edgeIds));
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(ev1v2.getId(), edgeIds);
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(ev1v3.getId(), edgeIds);
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(ev2v3.getId(), edgeIds);
        org.neolumin.vertexium.test.util.IterableUtils.assertContains(ev3v1.getId(), edgeIds);
    }

    // Test for performance
    //@Test
    @SuppressWarnings("unused")
    private void testFindRelatedEdgesPerformance() {
        int totalNumberOfVertices = 100;
        int totalNumberOfEdges = 10000;
        int totalVerticesToCheck = 100;

        Date startTime, endTime;
        Random random = new Random(100);

        startTime = new Date();
        List<Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < totalNumberOfVertices; i++) {
            vertices.add(graph.addVertex("v" + i, VISIBILITY_A, AUTHORIZATIONS_A));
        }
        graph.flush();
        endTime = new Date();
        long insertVerticesTime = endTime.getTime() - startTime.getTime();

        startTime = new Date();
        for (int i = 0; i < totalNumberOfEdges; i++) {
            Vertex outVertex = vertices.get(random.nextInt(vertices.size()));
            Vertex inVertex = vertices.get(random.nextInt(vertices.size()));
            graph.addEdge("e" + i, outVertex, inVertex, "", VISIBILITY_A, AUTHORIZATIONS_A);
        }
        graph.flush();
        endTime = new Date();
        long insertEdgesTime = endTime.getTime() - startTime.getTime();

        List<String> vertexIds = new ArrayList<>();
        for (int i = 0; i < totalVerticesToCheck; i++) {
            Vertex v = vertices.get(random.nextInt(vertices.size()));
            vertexIds.add(v.getId());
        }

        startTime = new Date();
        Iterable<String> edgeIds = toList(graph.findRelatedEdges(vertexIds, AUTHORIZATIONS_A));
        IterableUtils.count(edgeIds);
        endTime = new Date();
        long findRelatedEdgesTime = endTime.getTime() - startTime.getTime();

        LOGGER.info(String.format(
                "RESULTS\ntotalNumberOfVertices,totalNumberOfEdges,totalVerticesToCheck,insertVerticesTime,insertEdgesTime,findRelatedEdgesTime\n%d,%d,%d,%d,%d,%d",
                totalNumberOfVertices,
                totalNumberOfEdges,
                totalVerticesToCheck,
                insertVerticesTime,
                insertEdgesTime,
                findRelatedEdgesTime));
    }

    @Test
    public void testEmptyPropertyMutation() {
        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        v1.prepareMutation().save(AUTHORIZATIONS_A_AND_B);
    }

    @Test
    public void testTextIndex() throws Exception {
        graph.defineProperty("none").dataType(String.class).textIndexHint(TextIndexHint.NONE).define();
        graph.defineProperty("none").dataType(String.class).textIndexHint(TextIndexHint.NONE).define(); // try calling define twice
        graph.defineProperty("both").dataType(String.class).textIndexHint(TextIndexHint.ALL).define();
        graph.defineProperty("fullText").dataType(String.class).textIndexHint(TextIndexHint.FULL_TEXT).define();
        graph.defineProperty("exactMatch").dataType(String.class).textIndexHint(TextIndexHint.EXACT_MATCH).define();

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("none", "Test Value", VISIBILITY_A)
                .setProperty("both", "Test Value", VISIBILITY_A)
                .setProperty("fullText", "Test Value", VISIBILITY_A)
                .setProperty("exactMatch", "Test Value", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Vertex v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertEquals("Test Value", v1.getPropertyValue("none"));
        assertEquals("Test Value", v1.getPropertyValue("both"));
        assertEquals("Test Value", v1.getPropertyValue("fullText"));
        assertEquals("Test Value", v1.getPropertyValue("exactMatch"));

        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("both", TextPredicate.CONTAINS, "Test").vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("fullText", TextPredicate.CONTAINS, "Test").vertices()));
        Assert.assertEquals("exact match shouldn't match partials", 0, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("exactMatch", "Test").vertices()));
        Assert.assertEquals("un-indexed property shouldn't match partials", 0, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("none", "Test").vertices()));

        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("both", "Test Value").vertices()));
        Assert.assertEquals("default has predicate is equals which shouldn't work for full text", 0, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("fullText", "Test Value").vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("exactMatch", "Test Value").vertices()));
        if (IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("none", "Test Value").vertices()) != 0) {
            LOGGER.warn("default has predicate is equals which shouldn't work for un-indexed");
        }
    }

    @Test
    public void testTextIndexStreamingPropertyValue() throws Exception {
        graph.defineProperty("none").dataType(String.class).textIndexHint(TextIndexHint.NONE).define();
        graph.defineProperty("both").dataType(String.class).textIndexHint(TextIndexHint.ALL).define();
        graph.defineProperty("fullText").dataType(String.class).textIndexHint(TextIndexHint.FULL_TEXT).define();

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("none", StreamingPropertyValue.create("Test Value"), VISIBILITY_A)
                .setProperty("both", StreamingPropertyValue.create("Test Value"), VISIBILITY_A)
                .setProperty("fullText", StreamingPropertyValue.create("Test Value"), VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("both", TextPredicate.CONTAINS, "Test").vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("fullText", TextPredicate.CONTAINS, "Test").vertices()));
        Assert.assertEquals("un-indexed property shouldn't match partials", 0, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("none", "Test").vertices()));
        Assert.assertEquals("un-indexed property shouldn't match partials", 0, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("none", TextPredicate.CONTAINS, "Test").vertices()));
    }

    @Test
    public void testFieldBoost() throws Exception {
        if (!graph.isFieldBoostSupported()) {
            LOGGER.warn("Boost not supported");
            return;
        }

        graph.defineProperty("a")
                .dataType(String.class)
                .textIndexHint(TextIndexHint.ALL)
                .boost(1)
                .define();
        graph.defineProperty("b")
                .dataType(String.class)
                .textIndexHint(TextIndexHint.ALL)
                .boost(2)
                .define();

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("a", "Test Value", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("b", "Test Value", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        assertVertexIds(graph.query("Test", AUTHORIZATIONS_A).vertices(), new String[]{"v2", "v1"});
    }

    @Test
    public void testVertexBoost() throws Exception {
        if (!isEdgeBoostSupported()) {
            LOGGER.warn("Boost not supported");
            return;
        }

        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        Vertex v2 = graph.prepareVertex("v2", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        Vertex v3 = graph.prepareVertex("v3", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        graph.addEdge("e1", v3, v2, "link", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.flush();

        v1.prepareMutation().save(AUTHORIZATIONS_A_AND_B);
        v2.prepareMutation().save(AUTHORIZATIONS_A_AND_B);
        v3.prepareMutation().save(AUTHORIZATIONS_A_AND_B);

        assertVertexIds(graph.query(AUTHORIZATIONS_A).vertices(), new String[]{"v2", "v3", "v1"});
    }

    @Test
    public void testValueTypes() throws Exception {
        Date date = createDate(2014, 2, 24, 13, 0, 5);

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("int", 5, VISIBILITY_A)
                .setProperty("bigInteger", BigInteger.valueOf(10), VISIBILITY_A)
                .setProperty("bigDecimal", BigDecimal.valueOf(1.1), VISIBILITY_A)
                .setProperty("double", 5.6, VISIBILITY_A)
                .setProperty("float", 6.4f, VISIBILITY_A)
                .setProperty("string", "test", VISIBILITY_A)
                .setProperty("byte", (byte) 5, VISIBILITY_A)
                .setProperty("long", (long) 5, VISIBILITY_A)
                .setProperty("boolean", true, VISIBILITY_A)
                .setProperty("geopoint", new GeoPoint(77, -33), VISIBILITY_A)
                .setProperty("short", (short) 5, VISIBILITY_A)
                .setProperty("date", date, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("int", 5).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("double", 5.6).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("float", 6.4f).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("string", "test").vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("byte", 5).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("long", 5).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("boolean", true).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("short", 5).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("date", date).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("bigInteger", BigInteger.valueOf(10)).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("bigInteger", 10).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("bigDecimal", BigDecimal.valueOf(1.1)).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("bigDecimal", 1.1).vertices()));
        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("geopoint", GeoCompare.WITHIN, new GeoCircle(77, -33, 1)).vertices()));
    }

    @Test
    public void testChangeVisibilityVertex() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Vertex v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        v1.prepareMutation()
                .alterElementVisibility(VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertNull(v1);
        v1 = graph.getVertex("v1", AUTHORIZATIONS_B);
        assertNotNull(v1);

        // change to same visibility
        v1 = graph.getVertex("v1", AUTHORIZATIONS_B);
        v1.prepareMutation()
                .alterElementVisibility(VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertNull(v1);
        v1 = graph.getVertex("v1", AUTHORIZATIONS_B);
        assertNotNull(v1);
    }

    @Test
    public void testChangeVisibilityVertexProperties() {
        Metadata prop1Metadata = new Metadata();
        prop1Metadata.add("prop1_key1", "value1", VISIBILITY_EMPTY);

        Metadata prop2Metadata = new Metadata();
        prop2Metadata.add("prop2_key1", "value1", VISIBILITY_EMPTY);

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", prop1Metadata, VISIBILITY_EMPTY)
                .setProperty("prop2", "value2", prop2Metadata, VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);

        Vertex v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        v1.prepareMutation()
                .alterPropertyVisibility("prop1", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertNull(v1.getProperty("prop1"));
        assertNotNull(v1.getProperty("prop2"));

        Assert.assertEquals(1, IterableUtils.count(graph.query(AUTHORIZATIONS_A_AND_B).has("prop1", "value1").vertices()));
        Assert.assertEquals(0, IterableUtils.count(graph.query(AUTHORIZATIONS_A).has("prop1", "value1").vertices()));

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        Property v1Prop1 = v1.getProperty("prop1");
        assertNotNull(v1Prop1);
        Assert.assertEquals(1, toList(v1Prop1.getMetadata().entrySet()).size());
        assertEquals("value1", v1Prop1.getMetadata().getValue("prop1_key1"));
        assertNotNull(v1.getProperty("prop2"));

        // alter and set property in one mutation
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        v1.prepareMutation()
                .alterPropertyVisibility("prop1", VISIBILITY_A)
                .setProperty("prop1", "value1New", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        assertNotNull(v1.getProperty("prop1"));
        assertEquals("value1New", v1.getPropertyValue("prop1"));

        // alter visibility to the same visibility
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        v1.prepareMutation()
                .alterPropertyVisibility("prop1", VISIBILITY_A)
                .setProperty("prop1", "value1New2", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        assertNotNull(v1.getProperty("prop1"));
        assertEquals("value1New2", v1.getPropertyValue("prop1"));
    }

    @Test
    public void testChangeVisibilityEdge() {
        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);

        Vertex v2 = graph.prepareVertex("v2", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);

        graph.prepareEdge("e1", v1, v2, "", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        // test that we can see the edge with A and not B
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(v1.getEdges(Direction.BOTH, AUTHORIZATIONS_B)));
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(Direction.BOTH, AUTHORIZATIONS_A)));

        // change the edge
        Edge e1 = graph.getEdge("e1", AUTHORIZATIONS_A);
        e1.prepareMutation()
                .alterElementVisibility(VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        // test that we can see the edge with B and not A
        v1 = graph.getVertex("v1", AUTHORIZATIONS_B);
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(Direction.BOTH, AUTHORIZATIONS_B)));
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(v1.getEdges(Direction.BOTH, AUTHORIZATIONS_A)));

        // change the edge visibility to same
        e1 = graph.getEdge("e1", AUTHORIZATIONS_B);
        e1.prepareMutation()
                .alterElementVisibility(VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        // test that we can see the edge with B and not A
        v1 = graph.getVertex("v1", AUTHORIZATIONS_B);
        Assert.assertEquals(1, IterableUtils.count(v1.getEdges(Direction.BOTH, AUTHORIZATIONS_B)));
        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        Assert.assertEquals(0, IterableUtils.count(v1.getEdges(Direction.BOTH, AUTHORIZATIONS_A)));
    }

    @Test
    public void testChangeVisibilityOnBadPropertyName() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_EMPTY)
                .setProperty("prop2", "value2", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        try {
            graph.getVertex("v1", AUTHORIZATIONS_A)
                    .prepareMutation()
                    .alterPropertyVisibility("propBad", VISIBILITY_B)
                    .save(AUTHORIZATIONS_A_AND_B);
            fail("show throw");
        } catch (VertexiumException ex) {
            assertNotNull(ex);
        }
    }

    @Test
    public void testChangeVisibilityOnStreamingProperty() throws IOException {
        String expectedLargeValue = IOUtils.toString(new LargeStringInputStream(LARGE_PROPERTY_VALUE_SIZE));
        PropertyValue propSmall = new StreamingPropertyValue(new ByteArrayInputStream("value1".getBytes()), String.class);
        PropertyValue propLarge = new StreamingPropertyValue(new ByteArrayInputStream(expectedLargeValue.getBytes()), String.class);
        String largePropertyName = "propLarge/\\*!@#$%^&*()[]{}|";
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("propSmall", propSmall, VISIBILITY_A)
                .setProperty(largePropertyName, propLarge, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(2, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A).getProperties()));

        graph.getVertex("v1", AUTHORIZATIONS_A)
                .prepareMutation()
                .alterPropertyVisibility("propSmall", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(1, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A).getProperties()));

        graph.getVertex("v1", AUTHORIZATIONS_A)
                .prepareMutation()
                .alterPropertyVisibility(largePropertyName, VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);
        Assert.assertEquals(0, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A).getProperties()));

        Assert.assertEquals(2, IterableUtils.count(graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).getProperties()));
    }

    @Test
    public void testChangePropertyMetadata() {
        Metadata prop1Metadata = new Metadata();
        prop1Metadata.add("prop1_key1", "valueOld", VISIBILITY_EMPTY);

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", prop1Metadata, VISIBILITY_EMPTY)
                .setProperty("prop2", "value2", null, VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);

        Vertex v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        v1.prepareMutation()
                .setPropertyMetadata("prop1", "prop1_key1", "valueNew", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        assertEquals("valueNew", v1.getProperty("prop1").getMetadata().getEntry("prop1_key1", VISIBILITY_EMPTY).getValue());

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertEquals("valueNew", v1.getProperty("prop1").getMetadata().getEntry("prop1_key1", VISIBILITY_EMPTY).getValue());

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        v1.prepareMutation()
                .setPropertyMetadata("prop2", "prop2_key1", "valueNew", VISIBILITY_EMPTY)
                .save(AUTHORIZATIONS_A_AND_B);
        assertEquals("valueNew", v1.getProperty("prop2").getMetadata().getEntry("prop2_key1", VISIBILITY_EMPTY).getValue());

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        assertEquals("valueNew", v1.getProperty("prop2").getMetadata().getEntry("prop2_key1", VISIBILITY_EMPTY).getValue());
    }

    @Test
    public void testMetadata() {
        Vertex v1 = graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);

        ExistingElementMutation<Vertex> m = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B).prepareMutation();
        m.setPropertyMetadata(v1.getProperty("prop1", VISIBILITY_A), "metadata1", "metadata-value1aa", VISIBILITY_A);
        m.setPropertyMetadata(v1.getProperty("prop1", VISIBILITY_A), "metadata1", "metadata-value1ab", VISIBILITY_B);
        m.setPropertyMetadata(v1.getProperty("prop1", VISIBILITY_B), "metadata1", "metadata-value1bb", VISIBILITY_B);
        m.save(AUTHORIZATIONS_A_AND_B);

        v1 = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);

        Property prop1A = v1.getProperty("prop1", VISIBILITY_A);
        assertEquals(2, prop1A.getMetadata().entrySet().size());
        assertEquals("metadata-value1aa", prop1A.getMetadata().getValue("metadata1", VISIBILITY_A));
        assertEquals("metadata-value1ab", prop1A.getMetadata().getValue("metadata1", VISIBILITY_B));

        Property prop1B = v1.getProperty("prop1", VISIBILITY_B);
        assertEquals(1, prop1B.getMetadata().entrySet().size());
        assertEquals("metadata-value1bb", prop1B.getMetadata().getValue("metadata1", VISIBILITY_B));
    }

    @Test
    public void testIsVisibilityValid() {
        assertFalse(graph.isVisibilityValid(VISIBILITY_A, AUTHORIZATIONS_C));
        assertTrue(graph.isVisibilityValid(VISIBILITY_B, AUTHORIZATIONS_A_AND_B));
        assertTrue(graph.isVisibilityValid(VISIBILITY_B, AUTHORIZATIONS_B));
        assertTrue(graph.isVisibilityValid(VISIBILITY_EMPTY, AUTHORIZATIONS_A));
    }

    @Test
    public void testModifyVertexWithLowerAuthorizationThenOtherProperties() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_A)
                .setProperty("prop2", "value2", VISIBILITY_B)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();

        Vertex v1 = graph.getVertex("v1", AUTHORIZATIONS_A);
        v1.setProperty("prop1", "value1New", VISIBILITY_A, AUTHORIZATIONS_A_AND_B);
        graph.flush();

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("prop2", "value2")
                .vertices();
        assertVertexIds(vertices, new String[]{"v1"});
    }

    @Test
    public void testPartialUpdateOfVertex() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_A)
                .setProperty("prop2", "value2", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1New", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.flush();

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("prop2", "value2")
                .vertices();
        assertVertexIds(vertices, new String[]{"v1"});
    }

    @Test
    public void testAddVertexWithoutIndexing() {
        if (isDefaultSearchIndex()) {
            return;
        }

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_A)
                .setIndexHint(IndexHint.DO_NOT_INDEX)
                .save(AUTHORIZATIONS_A);
        graph.flush();

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("prop1", "value1")
                .vertices();
        assertVertexIds(vertices, new String[]{});
    }

    @Test
    public void testAlterVertexWithoutIndexing() {
        if (isDefaultSearchIndex()) {
            return;
        }

        graph.prepareVertex("v1", VISIBILITY_A)
                .setIndexHint(IndexHint.DO_NOT_INDEX)
                .save(AUTHORIZATIONS_A);
        graph.flush();

        Vertex v1 = graph.getVertex("v1", AUTHORIZATIONS_A_AND_B);
        v1.prepareMutation()
                .setProperty("prop1", "value1", VISIBILITY_A)
                .setIndexHint(IndexHint.DO_NOT_INDEX)
                .save(AUTHORIZATIONS_A);
        graph.flush();

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("prop1", "value1")
                .vertices();
        assertVertexIds(vertices, new String[]{});
    }

    @Test
    public void testAddEdgeWithoutIndexing() {
        if (isDefaultSearchIndex()) {
            return;
        }

        Vertex v1 = graph.addVertex("v1", VISIBILITY_A, AUTHORIZATIONS_A);
        Vertex v2 = graph.addVertex("v2", VISIBILITY_A, AUTHORIZATIONS_A);
        graph.prepareEdge("e1", v1, v2, "label1", VISIBILITY_A)
                .setProperty("prop1", "value1", VISIBILITY_A)
                .setIndexHint(IndexHint.DO_NOT_INDEX)
                .save(AUTHORIZATIONS_A);
        graph.flush();

        Iterable<Edge> edges = graph.query(AUTHORIZATIONS_A_AND_B)
                .has("prop1", "value1")
                .edges();
        assertEdgeIds(edges, new String[]{});
    }

    @Test
    public void testIteratorWithLessThanPageSizeResultsPageOne() {
        QueryParameters parameters = new QueryStringQueryParameters("*", AUTHORIZATIONS_EMPTY);
        parameters.setSkip(0);
        parameters.setLimit(5);
        DefaultGraphQueryIterable<Vertex> iterable = new DefaultGraphQueryIterable<>(parameters, getVertices(3), false, false);
        int count = 0;
        Iterator<Vertex> iterator = iterable.iterator();
        Vertex v = null;
        while (iterator.hasNext()) {
            count++;
            v = iterator.next();
            assertNotNull(v);
        }
        assertEquals(3, count);
        assertNotNull("v was null", v);
        assertEquals("2", v.getId());
    }

    @Test
    public void testIteratorWithPageSizeResultsPageOne() {
        QueryParameters parameters = new QueryStringQueryParameters("*", AUTHORIZATIONS_EMPTY);
        parameters.setSkip(0);
        parameters.setLimit(5);
        DefaultGraphQueryIterable<Vertex> iterable = new DefaultGraphQueryIterable<>(parameters, getVertices(5), false, false);
        int count = 0;
        Iterator<Vertex> iterator = iterable.iterator();
        Vertex v = null;
        while (iterator.hasNext()) {
            count++;
            v = iterator.next();
            assertNotNull(v);
        }
        assertEquals(5, count);
        assertNotNull("v was null", v);
        assertEquals("4", v.getId());
    }

    @Test
    public void testIteratorWithMoreThanPageSizeResultsPageOne() {
        QueryParameters parameters = new QueryStringQueryParameters("*", AUTHORIZATIONS_EMPTY);
        parameters.setSkip(0);
        parameters.setLimit(5);
        DefaultGraphQueryIterable<Vertex> iterable = new DefaultGraphQueryIterable<>(parameters, getVertices(7), false, false);
        int count = 0;
        Iterator<Vertex> iterator = iterable.iterator();
        Vertex v = null;
        while (iterator.hasNext()) {
            count++;
            v = iterator.next();
            assertNotNull(v);
        }
        assertEquals(5, count);
        assertNotNull("v was null", v);
        assertEquals("4", v.getId());
    }

    @Test
    public void testIteratorWithMoreThanPageSizeResultsPageTwo() {
        QueryParameters parameters = new QueryStringQueryParameters("*", AUTHORIZATIONS_EMPTY);
        parameters.setSkip(5);
        parameters.setLimit(5);
        DefaultGraphQueryIterable<Vertex> iterable = new DefaultGraphQueryIterable<>(parameters, getVertices(12), false, false);
        int count = 0;
        Iterator<Vertex> iterator = iterable.iterator();
        Vertex v = null;
        while (iterator.hasNext()) {
            count++;
            v = iterator.next();
            assertNotNull(v);
        }
        assertEquals(5, count);
        assertNotNull("v was null", v);
        assertEquals("9", v.getId());
    }

    @Test
    public void testIteratorWithMoreThanPageSizeResultsPageThree() {
        QueryParameters parameters = new QueryStringQueryParameters("*", AUTHORIZATIONS_EMPTY);
        parameters.setSkip(10);
        parameters.setLimit(5);
        DefaultGraphQueryIterable<Vertex> iterable = new DefaultGraphQueryIterable<>(parameters, getVertices(12), false, false);
        int count = 0;
        Iterator<Vertex> iterator = iterable.iterator();
        Vertex v = null;
        while (iterator.hasNext()) {
            count++;
            v = iterator.next();
            assertNotNull(v);
        }
        assertEquals(2, count);
        assertNotNull("v was null", v);
        assertEquals("11", v.getId());
    }

    @Test
    public void testGraphMetadata() {
        List<GraphMetadataEntry> existingMetadata = toList(graph.getMetadata());

        graph.setMetadata("test1", "value1");
        graph.setMetadata("test2", "value2");

        assertEquals("value1", graph.getMetadata("test1"));
        assertEquals("value2", graph.getMetadata("test2"));

        List<GraphMetadataEntry> newMetadata = toList(graph.getMetadata());
        assertEquals(existingMetadata.size() + 2, newMetadata.size());
    }

    @Test
    public void testSimilarityByText() {
        if (!graph.isQuerySimilarToTextSupported()) {
            LOGGER.warn("skipping test. Graph " + graph.getClass().getName() + " does not support query similar to text");
            return;
        }

        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("text", "Mary had a little lamb, His fleece was white as snow.", VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("text", "Mary had a little tiger, His fleece was white as snow.", VISIBILITY_B)
                .save(AUTHORIZATIONS_B);
        graph.prepareVertex("v3", VISIBILITY_A)
                .setProperty("text", "Mary had a little lamb.", VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.prepareVertex("v4", VISIBILITY_A)
                .setProperty("text", "His fleece was white as snow.", VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.prepareVertex("v5", VISIBILITY_A)
                .setProperty("text", "Mary had a little lamb, His fleece was black as snow.", VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.prepareVertex("v5", VISIBILITY_A)
                .setProperty("text", "Jack and Jill went up the hill to fetch a pail of water.", VISIBILITY_A)
                .save(AUTHORIZATIONS_A);
        graph.flush();

        List<Vertex> vertices = toList(
                graph.querySimilarTo(new String[]{"text"}, "Mary had a little lamb, His fleece was white as snow", AUTHORIZATIONS_A_AND_B)
                        .minTermFrequency(1)
                        .maxQueryTerms(25)
                        .minDocFrequency(1)
                        .maxDocFrequency(10)
                        .percentTermsToMatch(0.5f)
                        .boost(2.0f)
                        .vertices()
        );
        assertEquals(4, vertices.size());
        assertEquals("v1", vertices.get(0).getId());
        assertEquals("v2", vertices.get(1).getId());
        assertEquals("v4", vertices.get(2).getId());
        assertEquals("v3", vertices.get(3).getId());

        vertices = toList(
                graph.querySimilarTo(new String[]{"text"}, "Mary had a little lamb, His fleece was white as snow", AUTHORIZATIONS_A)
                        .minTermFrequency(1)
                        .maxQueryTerms(25)
                        .minDocFrequency(1)
                        .maxDocFrequency(10)
                        .percentTermsToMatch(0.5f)
                        .boost(2.0f)
                        .vertices()
        );
        assertEquals(3, vertices.size());
        assertEquals("v1", vertices.get(0).getId());
        assertEquals("v4", vertices.get(1).getId());
        assertEquals("v3", vertices.get(2).getId());
    }

    @Test
    public void testQuerySort() {
        graph.prepareVertex("v1", VISIBILITY_A)
                .setProperty("age", 25, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v2", VISIBILITY_A)
                .setProperty("age", 30, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v3", VISIBILITY_A)
                .setProperty("age", 40, VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);
        graph.prepareVertex("v4", VISIBILITY_A)
                .save(AUTHORIZATIONS_A_AND_B);

        Iterable<Vertex> vertices = graph.query(AUTHORIZATIONS_A)
                .sort("age", SortDirection.ASCENDING)
                .vertices();
        assertVertexIds(vertices, new String[]{"v1", "v2", "v3", "v4"});

        vertices = graph.query(AUTHORIZATIONS_A)
                .sort("age", SortDirection.DESCENDING)
                .vertices();
        assertVertexIds(vertices, new String[]{"v3", "v2", "v1", "v4"});
    }

    private List<Vertex> getVertices(long count) {
        List<Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vertex vertex = graph.addVertex(Integer.toString(i), VISIBILITY_EMPTY, AUTHORIZATIONS_EMPTY);
            vertices.add(vertex);
        }
        return vertices;
    }

    private boolean isDefaultSearchIndex() {
        if (!(graph instanceof GraphBaseWithSearchIndex)) {
            return false;
        }

        GraphBaseWithSearchIndex graphBaseWithSearchIndex = (GraphBaseWithSearchIndex) graph;
        return graphBaseWithSearchIndex.getSearchIndex() instanceof DefaultSearchIndex;
    }

    protected void assertVertexIds(Iterable<Vertex> vertices, String[] expectedIds) {
        String verticesIdsString = idsToString(vertices);
        String expectedIdsString = idsToString(expectedIds);
        List<Vertex> verticesList = toList(vertices);
        assertEquals("ids length mismatch found:[" + verticesIdsString + "] expected:[" + expectedIdsString + "]", expectedIds.length, verticesList.size());
        for (int i = 0; i < expectedIds.length; i++) {
            assertEquals("at offset: " + i + " found:[" + verticesIdsString + "] expected:[" + expectedIdsString + "]", expectedIds[i], verticesList.get(i).getId());
        }
    }

    private String idsToString(String[] ids) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String id : ids) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(id);
            first = false;
        }
        return sb.toString();
    }

    private String idsToString(Iterable<Vertex> vertices) {
        List<String> idsList = toList(new ConvertingIterable<Vertex, String>(vertices) {
            @Override
            protected String convert(Vertex o) {
                return o.getId();
            }
        });
        String[] idsArray = idsList.toArray(new String[idsList.size()]);
        return idsToString(idsArray);
    }

    private void assertEvents(GraphEvent... expectedEvents) {
        assertEquals("More graph events occurred than were asserted", expectedEvents.length, graphEvents.size());

        for (int i = 0; i < expectedEvents.length; i++) {
            assertEquals(expectedEvents[i], graphEvents.get(i));
        }
    }

    protected void assertEdgeIds(Iterable<Edge> edges, String[] ids) {
        List<Edge> edgesList = toList(edges);
        assertEquals("ids length mismatch", ids.length, edgesList.size());
        for (int i = 0; i < ids.length; i++) {
            assertEquals("at offset: " + i, ids[i], edgesList.get(i).getId());
        }
    }

    protected boolean disableUpdateEdgeCountInSearchIndex(Graph graph) {
        return false;
    }

    protected boolean disableEdgeIndexing(Graph graph) {
        try {
            if (!(graph instanceof GraphBaseWithSearchIndex)) {
                LOGGER.debug("Graph does not have a search index");
                return false;
            }

            SearchIndex searchIndex = ((GraphBaseWithSearchIndex) graph).getSearchIndex();

            Field configField = findPrivateField(searchIndex.getClass(), "config");
            if (configField == null) {
                LOGGER.debug("Could not find 'config' field");
                return false;
            }

            configField.setAccessible(true);

            Object config = configField.get(searchIndex);
            if (config == null) {
                LOGGER.debug("Could not get 'config' field");
                return false;
            }

            Field indexEdgesField = findPrivateField(config.getClass(), "indexEdges");
            if (indexEdgesField == null) {
                LOGGER.debug("Could not find 'indexEdgesField' field");
                return false;
            }

            indexEdgesField.setAccessible(true);

            indexEdgesField.set(config, false);

            return true;
        } catch (Exception ex) {
            throw new VertexiumException("Could not disableEdgeIndexing", ex);
        }
    }

    private Field findPrivateField(Class clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findPrivateField(clazz.getSuperclass(), name);
            }
            return null;
        }
    }

    protected abstract boolean isEdgeBoostSupported();
}