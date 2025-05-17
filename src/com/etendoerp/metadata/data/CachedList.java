package com.etendoerp.metadata.data;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.openbravo.dal.core.OBContext;

class CachedList<T> {
    final long lastUpdated;
    final String context;
    final List<T> data;

    private CachedList(List<T> data, long lastUpdated) {
        this.data = data;
        this.lastUpdated = lastUpdated;
        this.context = getContext();
    }

    private static String getContext() {
        OBContext obContext = OBContext.getOBContext();

        return obContext != null ? obContext.toString() : "";
    }

    static <T> List<T> fetchAndFilter(String id, long lastUpdated, Map<String, CachedList<T>> cache,
        Supplier<List<T>> fetchList, Predicate<T> filter) {
        CachedList<T> cached = cache.get(id);
        String context = getContext();

        if (cached != null && cached.lastUpdated == lastUpdated && context.equals(cached.context)) {
            return cached.data;
        }

        List<T> filteredList = fetchList.get().stream().filter(filter).collect(Collectors.toList());
        cache.put(id, new CachedList<>(filteredList, lastUpdated));

        return filteredList;
    }
}
