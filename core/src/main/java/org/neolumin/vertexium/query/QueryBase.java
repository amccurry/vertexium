package org.neolumin.vertexium.query;

import org.neolumin.vertexium.*;
import org.neolumin.vertexium.util.FilterIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.neolumin.vertexium.util.IterableUtils.toList;

public abstract class QueryBase implements Query, SimilarToGraphQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryBase.class);
    private final Graph graph;
    private final Map<String, PropertyDefinition> propertyDefinitions;
    private final QueryParameters parameters;

    protected QueryBase(Graph graph, String queryString, Map<String, PropertyDefinition> propertyDefinitions, Authorizations authorizations) {
        this.graph = graph;
        this.propertyDefinitions = propertyDefinitions;
        this.parameters = new QueryStringQueryParameters(queryString, authorizations);
    }

    protected QueryBase(Graph graph, String[] similarToFields, String similarToText, Map<String, PropertyDefinition> propertyDefinitions, Authorizations authorizations) {
        this.graph = graph;
        this.propertyDefinitions = propertyDefinitions;
        this.parameters = new SimilarToTextQueryParameters(similarToFields, similarToText, authorizations);
    }

    @Override
    public Iterable<Vertex> vertices() {
        return vertices(FetchHint.ALL);
    }

    @Override
    public abstract Iterable<Vertex> vertices(EnumSet<FetchHint> fetchHints);

    @Override
    public Iterable<Edge> edges() {
        return edges(FetchHint.ALL);
    }

    @Override
    public abstract Iterable<Edge> edges(EnumSet<FetchHint> fetchHints);

    @Override
    public Iterable<Edge> edges(final String label, EnumSet<FetchHint> fetchHints) {
        return new FilterIterable<Edge>(edges()) {
            @Override
            protected boolean isIncluded(Edge o) {
                return label.equals(o.getLabel());
            }
        };
    }

    @Override
    public Iterable<Edge> edges(final String label) {
        return edges(label, FetchHint.ALL);
    }

    @Override
    public <T> Query range(String propertyName, T startValue, T endValue) {
        this.parameters.addHasContainer(new HasContainer(propertyName, Compare.GREATER_THAN_EQUAL, startValue, this.propertyDefinitions));
        this.parameters.addHasContainer(new HasContainer(propertyName, Compare.LESS_THAN_EQUAL, endValue, this.propertyDefinitions));
        return this;
    }

    @Override
    public <T> Query has(String propertyName, T value) {
        this.parameters.addHasContainer(new HasContainer(propertyName, Compare.EQUAL, value, this.propertyDefinitions));
        return this;
    }

    @Override
    public <T> Query has(String propertyName, Predicate predicate, T value) {
        this.parameters.addHasContainer(new HasContainer(propertyName, predicate, value, this.propertyDefinitions));
        return this;
    }

    @Override
    public Query skip(int count) {
        this.parameters.setSkip(count);
        return this;
    }

    @Override
    public Query limit(int count) {
        this.parameters.setLimit(count);
        return this;
    }

    public Graph getGraph() {
        return graph;
    }

    public QueryParameters getParameters() {
        return parameters;
    }

    protected Map<String, PropertyDefinition> getPropertyDefinitions() {
        return propertyDefinitions;
    }

    public static <T extends Element> Iterable<T> sort(Iterable<T> iterable, List<SortContainer> sortParameters) {
        LOGGER.warn("fetching all items to perform sort");
        List<T> items = toList(iterable);
        Collections.sort(items, new SortComparator<T>(sortParameters));
        return items;
    }

    public static class HasContainer {
        public String key;
        public Object value;
        public Predicate predicate;
        private final Map<String, PropertyDefinition> propertyDefinitions;

        public HasContainer(final String key, final Predicate predicate, final Object value, Map<String, PropertyDefinition> propertyDefinitions) {
            this.key = key;
            this.value = value;
            this.predicate = predicate;
            this.propertyDefinitions = propertyDefinitions;
        }

        public boolean isMatch(Element elem) {
            return this.predicate.evaluate(elem.getProperties(this.key), this.value, this.propertyDefinitions);
        }
    }

    public static class SortContainer {
        private final String propertyName;
        private final SortDirection direction;

        public SortContainer(String propertyName, SortDirection direction) {
            this.propertyName = propertyName;
            this.direction = direction;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public SortDirection getDirection() {
            return direction;
        }

        public <T extends Element> int compare(T o1, T o2) {
            List<Object> o1Values = toList(o1.getPropertyValues(getPropertyName()));
            List<Object> o2Values = toList(o2.getPropertyValues(getPropertyName()));
            if (o1Values.size() == 0 && o2Values.size() == 0) {
                return 0;
            }

            // put items with no values after items with values
            if (o1Values.size() == 0 && o2Values.size() > 0) {
                return 1;
            }
            if (o1Values.size() > 0 && o2Values.size() == 0) {
                return -1;
            }

            for (Object o1Value : o1Values) {
                for (Object o2Value : o2Values) {
                    int r = compareValues(o1Value, o2Value);
                    if (r != 0) {
                        return r;
                    }
                }
            }
            return 0;
        }

        protected int compareValues(Object o1Value, Object o2Value) {
            int r = 0;
            if (o1Value.getClass().isAssignableFrom(o2Value.getClass())
                    || o2Value.getClass().isAssignableFrom(o1Value.getClass())
                    && o1Value instanceof Comparable) {
                r = ((Comparable) o1Value).compareTo(o2Value);
            }
            return getDirection() == SortDirection.ASCENDING ? r : -r;
        }
    }

    @Override
    public SimilarToGraphQuery minTermFrequency(int minTermFrequency) {
        if (!(parameters instanceof SimilarToQueryParameters)) {
            throw new VertexiumException("Invalid query parameters, expected " + SimilarToQueryParameters.class.getName() + " found " + parameters.getClass().getName());
        }
        ((SimilarToQueryParameters) this.parameters).setMinTermFrequency(minTermFrequency);
        return this;
    }

    @Override
    public SimilarToGraphQuery maxQueryTerms(int maxQueryTerms) {
        if (!(parameters instanceof SimilarToQueryParameters)) {
            throw new VertexiumException("Invalid query parameters, expected " + SimilarToQueryParameters.class.getName() + " found " + parameters.getClass().getName());
        }
        ((SimilarToQueryParameters) this.parameters).setMaxQueryTerms(maxQueryTerms);
        return this;
    }

    @Override
    public SimilarToGraphQuery minDocFrequency(int minDocFrequency) {
        if (!(parameters instanceof SimilarToQueryParameters)) {
            throw new VertexiumException("Invalid query parameters, expected " + SimilarToQueryParameters.class.getName() + " found " + parameters.getClass().getName());
        }
        ((SimilarToQueryParameters) this.parameters).setMinDocFrequency(minDocFrequency);
        return this;
    }

    @Override
    public SimilarToGraphQuery maxDocFrequency(int maxDocFrequency) {
        if (!(parameters instanceof SimilarToQueryParameters)) {
            throw new VertexiumException("Invalid query parameters, expected " + SimilarToQueryParameters.class.getName() + " found " + parameters.getClass().getName());
        }
        ((SimilarToQueryParameters) this.parameters).setMaxDocFrequency(maxDocFrequency);
        return this;
    }

    @Override
    public SimilarToGraphQuery percentTermsToMatch(float percentTermsToMatch) {
        if (!(parameters instanceof SimilarToQueryParameters)) {
            throw new VertexiumException("Invalid query parameters, expected " + SimilarToQueryParameters.class.getName() + " found " + parameters.getClass().getName());
        }
        ((SimilarToQueryParameters) this.parameters).setPercentTermsToMatch(percentTermsToMatch);
        return this;
    }

    @Override
    public SimilarToGraphQuery boost(float boost) {
        if (!(parameters instanceof SimilarToQueryParameters)) {
            throw new VertexiumException("Invalid query parameters, expected " + SimilarToQueryParameters.class.getName() + " found " + parameters.getClass().getName());
        }
        ((SimilarToQueryParameters) this.parameters).setBoost(boost);
        return this;
    }

    @Override
    public Query sort(String propertyName, SortDirection direction) {
        parameters.addSortParameter(new SortContainer(propertyName, direction));
        return this;
    }
}
