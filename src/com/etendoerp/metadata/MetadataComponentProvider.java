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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.client.kernel.BaseComponentProvider;
import org.openbravo.client.kernel.Component;
import org.openbravo.client.kernel.ComponentProvider;

/**
 * Component Provider for the Metadata module.
 * <p>
 * This provider is responsible for registering global static resources required
 * by the
 * metadata module, such as JavaScript files for UI flags.
 * </p>
 */
@ApplicationScoped
@ComponentProvider.Qualifier(MetadataComponentProvider.METADATA_COMPONENT_TYPE)
public class MetadataComponentProvider extends BaseComponentProvider {

    public static final String METADATA_COMPONENT_TYPE = "SMF_OKR_Metadata";

    private static final String JS_PATH = "web/com.etendoerp.metadata/js/";
    static final String UI_FLAGS_JS = JS_PATH + "okr-ui-flags.js";
    static final String ALERT_COUNT_BRIDGE_JS = JS_PATH + "alert-count-bridge.js";

    /**
     * Retrieves a specific component.
     * <p>
     * This implementation does not support retrieving individual components and
     * will
     * always throw an {@link IllegalArgumentException}.
     * </p>
     *
     * @param componentId
     *                    The ID of the component to retrieve.
     * @param parameters
     *                    A map of parameters for the component.
     * @return Nothing, as this method always throws an exception.
     * @throws IllegalArgumentException
     *                                  Always thrown as this provider does not
     *                                  support component retrieval.
     */
    @Override
    public Component getComponent(String componentId, Map<String, Object> parameters) {
        throw new IllegalArgumentException(
                "Component id " + componentId + " not supported.");
    }

    /**
     * Retrieves the list of global component resources.
     * <p>
     * Registers the {@code okr-ui-flags.js} file (kiosk mode flags) and the
     * {@code alert-count-bridge.js} file (notifies the new UI of pending alert count
     * changes) as static resources.
     * These resources are included in the new UI mode (OB3) but not in Classic mode.
     * </p>
     *
     * @return A list of {@link ComponentResource} containing the registered
     *         resources.
     */
    @Override
    public List<ComponentResource> getGlobalComponentResources() {
        final List<ComponentResource> resources = new ArrayList<>();

        resources.add(createStaticResource(UI_FLAGS_JS, false));
        resources.add(createStaticResource(ALERT_COUNT_BRIDGE_JS, false));

        return resources;
    }
}