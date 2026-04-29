package com.etendoerp.metadata.widgets.resolvers;

import com.etendoerp.metadata.widgets.WidgetDataContext;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotResolverTest {
    @Test
    void resolveNullUrlReturnsEmptyMessages() throws Exception {
        WidgetDataContext ctx = mock(WidgetDataContext.class);
        when(ctx.classString("3")).thenReturn(null);

        JSONObject result = new CopilotResolver().resolve(ctx);
        assertTrue(result.has("messages"));
        assertEquals(0, result.getJSONArray("messages").length());
    }
}
