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
