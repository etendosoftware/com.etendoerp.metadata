package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HTMLResolverTest {
    @Test
    void getTypeReturnsHtml() {
        assertEquals("HTML", new HTMLResolver().getType());
    }

    @Test
    void resolveReturnsDescriptionAsContent() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("4")).thenReturn("<p>Hello</p>");

        JSONObject result = new HTMLResolver().resolve(ctx);
        assertEquals("<p>Hello</p>", result.getString("content"));
    }
}
