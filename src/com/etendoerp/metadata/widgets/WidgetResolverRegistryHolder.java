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
import com.etendoerp.metadata.widgets.resolvers.CalendarResolver;
import com.etendoerp.metadata.widgets.resolvers.URLResolver;

/**
 * Static holder for the singleton WidgetResolverRegistry, used in non-CDI contexts
 * (e.g., from WidgetDataService which is constructed via ServiceFactory, not Weld).
 */
public class WidgetResolverRegistryHolder {
    private static WidgetResolverRegistry instance;

    private WidgetResolverRegistryHolder() { }

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
        instance.register(new CalendarResolver());
    }

    public static WidgetResolverRegistry getInstance() { return instance; }
    public static void setInstance(WidgetResolverRegistry r) { instance = r; }
}
