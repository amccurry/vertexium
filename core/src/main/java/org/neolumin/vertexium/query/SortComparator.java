package org.neolumin.vertexium.query;

import org.neolumin.vertexium.Element;

import java.util.Comparator;
import java.util.List;

public class SortComparator<T extends Element> implements Comparator<T> {
    private final List<QueryBase.SortContainer> sortParameters;

    public SortComparator(List<QueryBase.SortContainer> sortParameters) {
        this.sortParameters = sortParameters;
    }

    @Override
    public int compare(T o1, T o2) {
        for (QueryBase.SortContainer sc : sortParameters) {
            int r = sc.compare(o1, o2);
            if (r != 0) {
                return r;
            }
        }
        return 0;
    }
}
