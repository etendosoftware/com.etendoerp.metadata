package com.etendoerp.metadata.widgets;

import com.etendoerp.metadata.widgets.resolvers.CopilotResolver;
import com.etendoerp.metadata.widgets.resolvers.FavoritesResolver;
import com.etendoerp.metadata.widgets.resolvers.HTMLResolver;
import com.etendoerp.metadata.widgets.resolvers.KPIResolver;
import com.etendoerp.metadata.widgets.resolvers.NotificationResolver;
import com.etendoerp.metadata.widgets.resolvers.ProcessResolver;
import com.etendoerp.metadata.widgets.resolvers.QueryListResolver;
import com.etendoerp.metadata.widgets.resolvers.RecentDocsResolver;
import com.etendoerp.metadata.widgets.resolvers.RecentlyViewedResolver;
import com.etendoerp.metadata.widgets.resolvers.StockAlertResolver;
import com.etendoerp.metadata.widgets.resolvers.URLResolver;

/**
 * Static holder for the singleton WidgetResolverRegistry, used in non-CDI contexts
 * (e.g., from WidgetDataService which is constructed via ServiceFactory, not Weld).
 */
public class WidgetResolverRegistryHolder {
    private static final WidgetResolverRegistry instance;

    static {
        instance = new WidgetResolverRegistry();
        instance.register(new RecentDocsResolver());
        instance.register(new RecentlyViewedResolver());
        instance.register(new FavoritesResolver());
        instance.register(new NotificationResolver());
        instance.register(new StockAlertResolver());
        instance.register(new KPIResolver());
        instance.register(new CopilotResolver());
        instance.register(new QueryListResolver());
        instance.register(new HTMLResolver());
        instance.register(new URLResolver());
        instance.register(new ProcessResolver());
    }

    public static WidgetResolverRegistry getInstance() { return instance; }
    public static void setInstance(WidgetResolverRegistry r) { instance = r; }
}
