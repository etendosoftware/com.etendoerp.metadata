/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.widgets;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Weld @ApplicationScoped singleton that indexes all WidgetDataResolver CDI beans by type.
 * The /meta/widget/{id}/data endpoint calls getResolver(type) to find the right handler.
 */
@ApplicationScoped
public class WidgetResolverRegistry {

    private final Map<String, WidgetDataResolver> byType = new HashMap<>();

    /** CDI injection point — Weld injects all WidgetDataResolver beans here at startup. */
    @Inject
    public void setResolvers(Instance<WidgetDataResolver> resolvers) {
        for (WidgetDataResolver r : resolvers) {
            byType.put(r.getType(), r);
        }
    }

    /**
     * Registers a resolver manually (used by unit tests to inject mocks without CDI).
     *
     * @param resolver the resolver to register
     */
    public void register(WidgetDataResolver resolver) {
        byType.put(resolver.getType(), resolver);
    }

    /**
     * Returns the resolver for the given TYPE string, or null if none is registered.
     * The caller should fall back to ProxyResolver when this returns null and
     * EXTERNAL_DATA_URL is set.
     *
     * @param type the widget type key
     * @return the matching resolver, or {@code null}
     */
    public WidgetDataResolver getResolver(String type) {
        return byType.get(type);
    }
}
