package org.neolumin.vertexium.query;

import org.neolumin.vertexium.Element;

import java.util.Iterator;
import java.util.List;

public class SortedIterable<T extends Element> implements Iterable<T> {
    private final Iterable<T> items;

    public SortedIterable(Iterable<T> iterable, List<QueryBase.SortContainer> sortParameters) {
        items = QueryBase.sort(iterable, sortParameters);
    }

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    public static <T extends Element> Iterable<T> wrapIfNeeded(Iterable<T> items, List<QueryBase.SortContainer> sortParameters) {
        if (sortParameters.size() > 0) {
            return new SortedIterable<>(items, sortParameters);
        }
        return items;
    }
}
