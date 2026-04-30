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
