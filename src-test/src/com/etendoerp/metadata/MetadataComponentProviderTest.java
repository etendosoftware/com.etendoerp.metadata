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
     * Verifies that the returned list contains the expected resource.
     */
    @Test
    void testGetGlobalComponentResources() {
        MetadataComponentProvider provider = new MetadataComponentProvider();
        List<ComponentResource> resources = provider.getGlobalComponentResources();

        assertNotNull(resources);
        assertEquals(1, resources.size());

        ComponentResource resource = resources.get(0);
        assertEquals("web/com.etendoerp.metadata/js/okr-ui-flags.js", resource.getPath());
        assertEquals(ComponentResource.ComponentResourceType.Static, resource.getType());

        // Verify valid apps based on createStaticResource(path, false)
        // createStaticResource(path, false) calls createStaticResource(path, false,
        // true) implicitly?
        // No, createStaticResource(path, false) calls createComponentResource and adds
        // APP_OB3.
        // And if includeAlsoInClassicMode is false, it does NOT add APP_CLASSIC.

        // Let's verify the valid apps list
        List<String> validApps = resource.getValidForAppList();
        assertNotNull(validApps);
        assertEquals(1, validApps.size());
        assertEquals(ComponentResource.APP_OB3, validApps.get(0));
    }
}
