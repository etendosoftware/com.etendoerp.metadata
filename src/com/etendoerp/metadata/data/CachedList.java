package com.etendoerp.metadata.data;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.dal.core.OBContext;

class CachedList<T> {
  protected static final Logger logger = LogManager.getLogger(CachedList.class);
  protected static final String SEPARATOR = "#";
  final long lastUpdated;
  final List<T> data;

  private CachedList(List<T> data, long lastUpdated) {
    this.data = data;
    this.lastUpdated = lastUpdated;
  }

  private static String getContext() {
    OBContext obContext = OBContext.getOBContext();

    return obContext != null ? obContext.toString() : "";
  }

  private static String getCacheKey(String id) {
    String context = getContext();

    return id.concat(SEPARATOR).concat(context);
  }

  static <T> List<T> fetchAndFilter(String id, long lastUpdated, Map<String, CachedList<T>> cache,
      Supplier<List<T>> fetchList, Predicate<T> filter) {
    String cacheKey = getCacheKey(id);
    CachedList<T> cached = cache.get(cacheKey);

    if (cached != null && cached.lastUpdated == lastUpdated) {
      return cached.data;
    }

    logger.info("Cache miss - {}", cacheKey);

    List<T> filteredList = fetchList.get().stream().filter(filter).collect(Collectors.toList());
    cache.put(cacheKey, new CachedList<>(filteredList, lastUpdated));

    return filteredList;
  }
}
