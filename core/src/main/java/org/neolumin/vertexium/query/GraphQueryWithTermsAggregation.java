package org.neolumin.vertexium.query;

public interface GraphQueryWithTermsAggregation extends GraphQuery {
    GraphQueryWithTermsAggregation addTermsAggregation(String aggregationName, String field);
}
