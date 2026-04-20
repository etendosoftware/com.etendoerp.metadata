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

    /** Package-visible for unit tests to inject mocks without CDI. */
    void register(WidgetDataResolver resolver) {
        byType.put(resolver.getType(), resolver);
    }

    /**
     * Returns the resolver for the given TYPE string, or null if none is registered.
     * The caller should fall back to ProxyResolver when this returns null and
     * EXTERNAL_DATA_URL is set.
     */
    public WidgetDataResolver getResolver(String type) {
        return byType.get(type);
    }
}
