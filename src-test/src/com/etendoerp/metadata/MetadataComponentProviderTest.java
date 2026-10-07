/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openbravo.client.kernel.BaseComponentProvider.ComponentResource;

/**
 * Test class for MetadataComponentProvider.
 */
class MetadataComponentProviderTest {

    private static final String UI_FLAGS_JS = "web/com.etendoerp.metadata/js/okr-ui-flags.js";
    private static final String ALERT_COUNT_BRIDGE_JS = "web/com.etendoerp.metadata/js/alert-count-bridge.js";

    /**
     * Test getComponent method.
     * Expects IllegalArgumentException as the method is not supported.
     */
    @Test
    void testGetComponent() {
        MetadataComponentProvider provider = new MetadataComponentProvider();
        assertThrows(IllegalArgumentException.class, () ->
            provider.getComponent("anyId", Collections.emptyMap())
        );
    }

    /**
     * Test getGlobalComponentResources method.
     * Verifies that the kiosk mode flags script and the alert count bridge script are
     * registered, in that order, as static resources valid only for the new UI (OB3).
     */
    @Test
    void testGetGlobalComponentResources() {
        MetadataComponentProvider provider = new MetadataComponentProvider();
        List<ComponentResource> resources = provider.getGlobalComponentResources();

        assertNotNull(resources);
        assertEquals(2, resources.size());
        assertOb3StaticResource(resources.get(0), UI_FLAGS_JS);
        assertOb3StaticResource(resources.get(1), ALERT_COUNT_BRIDGE_JS);
    }

    /**
     * Asserts that a resource is a static resource with the given path, included in the
     * new UI mode (OB3) but not in Classic mode ({@code createStaticResource(path, false)}).
     *
     * @param resource
     *     the resource to check
     * @param expectedPath
     *     the expected resource path
     */
    private static void assertOb3StaticResource(ComponentResource resource, String expectedPath) {
        assertEquals(expectedPath, resource.getPath());
        assertEquals(ComponentResource.ComponentResourceType.Static, resource.getType());

        List<String> validApps = resource.getValidForAppList();
        assertNotNull(validApps);
        assertEquals(Collections.singletonList(ComponentResource.APP_OB3), validApps);
    }
}
