package org.neolumin.vertexium.query;

public interface IterableWithGeohashResults<T> extends Iterable<T> {
    GeohashResult getGeohashResults(String name);
}
