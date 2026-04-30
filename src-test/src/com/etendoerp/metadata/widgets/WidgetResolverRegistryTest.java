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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WidgetResolverRegistryTest {

    @Test
    void getResolverByTypeReturnsMatchingResolver() {
        WidgetDataResolver htmlResolver = mock(WidgetDataResolver.class);
        when(htmlResolver.getType()).thenReturn("HTML");

        WidgetResolverRegistry registry = new WidgetResolverRegistry();
        registry.register(htmlResolver);

        WidgetDataResolver found = registry.getResolver("HTML");
        assertNotNull(found);
        assertEquals("HTML", found.getType());
    }

    @Test
    void getResolverByTypeReturnsNullForUnknownType() {
        WidgetResolverRegistry registry = new WidgetResolverRegistry();
        assertNull(registry.getResolver("UNKNOWN"));
    }

    @Test
    void registerDuplicateTypeLastOneWins() {
        WidgetDataResolver r1 = mock(WidgetDataResolver.class);
        WidgetDataResolver r2 = mock(WidgetDataResolver.class);
        when(r1.getType()).thenReturn("HTML");
        when(r2.getType()).thenReturn("HTML");

        WidgetResolverRegistry registry = new WidgetResolverRegistry();
        registry.register(r1);
        registry.register(r2);

        assertSame(r2, registry.getResolver("HTML"));
    }
}
