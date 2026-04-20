package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class URLResolverTest {
    @Test
    void getType_returnsURL() {
        assertEquals("URL", new URLResolver().getType());
    }

    @Test
    void resolve_returnsExternalDataUrl() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn("https://example.com");

        JSONObject result = new URLResolver().resolve(ctx);
        assertEquals("https://example.com", result.getString("url"));
        assertTrue(result.getBoolean("sandbox"));
    }
}
