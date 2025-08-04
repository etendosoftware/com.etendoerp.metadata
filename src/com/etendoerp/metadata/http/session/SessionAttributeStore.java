/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

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
