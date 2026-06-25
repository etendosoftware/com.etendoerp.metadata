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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WidgetResolverRegistryHolderTest {

    private WidgetResolverRegistry originalInstance;

    @AfterEach
    void restoreOriginal() {
        if (originalInstance != null) {
            WidgetResolverRegistryHolder.setInstance(originalInstance);
        }
    }

    @Test
    void getInstanceReturnsNonNull() {
        assertNotNull(WidgetResolverRegistryHolder.getInstance());
    }

    @Test
    void staticInitRegistersDefaultResolvers() {
        WidgetResolverRegistry registry = WidgetResolverRegistryHolder.getInstance();
        assertNotNull(registry.getResolver("HTML"));
        assertNotNull(registry.getResolver("KPI"));
        assertNotNull(registry.getResolver("COPILOT"));
        assertNotNull(registry.getResolver("FAVORITES"));
        assertNotNull(registry.getResolver("NOTIFICATION"));
        assertNotNull(registry.getResolver("STOCK_ALERT"));
        assertNotNull(registry.getResolver("QUERY_LIST"));
        assertNotNull(registry.getResolver("RECENT_DOCS"));
        assertNotNull(registry.getResolver("RECENTLY_VIEWED"));
        assertNotNull(registry.getResolver("URL"));
        assertNotNull(registry.getResolver("PROCESS"));
        assertNotNull(registry.getResolver("CALENDAR"));
    }

    @Test
    void setInstanceReplacesRegistry() {
        originalInstance = WidgetResolverRegistryHolder.getInstance();
        WidgetResolverRegistry custom = new WidgetResolverRegistry();
        WidgetResolverRegistryHolder.setInstance(custom);
        assertSame(custom, WidgetResolverRegistryHolder.getInstance());
    }
}
