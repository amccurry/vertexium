package org.neolumin.vertexium.query;

public interface GraphQueryWithGeohashAggregation extends GraphQuery {
    GraphQueryWithGeohashAggregation addGeohashAggregation(String aggregationName, String field, int precision);
}
