package com.etendoerp.metadata.http.session;

import com.etendoerp.redis.interfaces.CachedConcurrentMap;
import java.util.Map;
import java.util.HashMap;

public class SessionAttributeStore {
  private static final String PREFIX = "session_attrs:";
  private static final CachedConcurrentMap<String, Map<String, Object>> store = new CachedConcurrentMap<>(PREFIX);

  public Map<String, Object> getAttributes(String sessionId) {
    return store.getOrDefault(sessionId, new HashMap<>());
  }

  public void setAttribute(String sessionId, String name, Object value) {
    store.computeIfAbsent(sessionId, k -> new HashMap<>()).put(name, value);
  }

  public Object getAttribute(String sessionId, String name) {
    return getAttributes(sessionId).get(name);
  }

  public void removeAttribute(String sessionId, String name) {
    getAttributes(sessionId).remove(name);
  }

  public void removeAllAttributes(String sessionId) {
    store.remove(sessionId);
  }
}
