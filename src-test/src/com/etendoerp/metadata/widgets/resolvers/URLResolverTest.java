package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class URLResolverTest {
    @Test
    void getTypeReturnsUrl() {
        assertEquals("URL", new URLResolver().getType());
    }

    @Test
    void resolveReturnsExternalDataUrl() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn("https://example.com");

        JSONObject result = new URLResolver().resolve(ctx);
        assertEquals("https://example.com", result.getString("url"));
        assertTrue(result.getBoolean("sandbox"));
    }
}
