package com.etendoerp.metadata.widgets;

/**
 * Static holder for the singleton WidgetResolverRegistry, used in non-CDI contexts
 * (e.g., from WidgetDataService which is constructed via ServiceFactory, not Weld).
 */
public class WidgetResolverRegistryHolder {
    private static WidgetResolverRegistry instance = new WidgetResolverRegistry();

    public static WidgetResolverRegistry getInstance() { return instance; }
    public static void setInstance(WidgetResolverRegistry r) { instance = r; }
}
