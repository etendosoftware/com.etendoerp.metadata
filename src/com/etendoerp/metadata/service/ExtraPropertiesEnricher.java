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

package com.etendoerp.metadata.service;

import com.etendoerp.metadata.utils.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.model.domaintype.DomainType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class that scans the DAL entity model to determine which FK properties on a given
 * entity point to entities that have a Color-typed column (AD_Reference ID {@value Constants#COLOR_REFERENCE_ID}).
 *
 * <p>The resulting comma-separated path string (e.g. {@code "priority.color"}) is suitable for
 * use as the {@code _extraProperties} parameter in datasource fetch requests, which causes
 * {@code DataToJsonConverter} to include the nested color value in each row
 * (e.g. {@code "priority$color"}).</p>
 *
 * <p>Results are cached per entity name to avoid repeated model traversals on every
 * datasource fetch request.</p>
 */
public class ExtraPropertiesEnricher {

    private static final Logger log4j = LogManager.getLogger(ExtraPropertiesEnricher.class);

    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private ExtraPropertiesEnricher() {
        // Utility class – not instantiable
    }

    /**
     * Returns the {@code _extraProperties} string for the given entity.
     *
     * <p>The returned string is a comma-separated list of property paths in the form
     * {@code "fkProp.colorProp"} (e.g. {@code "priority.color"}). An empty string is
     * returned when no FK property references an entity with a Color column, or when the
     * entity cannot be resolved.</p>
     *
     * <p>Results are cached; call {@link #clearCache()} in tests to reset state.</p>
     *
     * @param entityName the DAL entity name (e.g. {@code "ETASK_TaskType"})
     * @return comma-separated extra-property paths, never {@code null}
     */
    public static String getExtraProperties(String entityName) {
        return CACHE.computeIfAbsent(entityName, ExtraPropertiesEnricher::buildExtraProperties);
    }

    /**
     * Clears the internal per-entity cache. Intended for use in tests only.
     */
    static void clearCache() {
        CACHE.clear();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String buildExtraProperties(String entityName) {
        try {
            Entity entity = ModelProvider.getInstance().getEntity(entityName, false);
            if (entity == null) {
                return "";
            }

            List<String> parts = new ArrayList<>();
            for (Property prop : entity.getProperties()) {
                if (!prop.isOneToMany() && !prop.isId() && !prop.isPrimitive()) {
                    Entity targetEntity = prop.getTargetEntity();
                    if (targetEntity != null) {
                        Property colorProp = findColorProperty(targetEntity);
                        if (colorProp != null) {
                            parts.add(prop.getName() + "." + colorProp.getName());
                        }
                    }
                }
            }

            return String.join(",", parts);
        } catch (Exception e) {
            log4j.warn("ExtraPropertiesEnricher: error building extra properties for entity {}: {}",
                    entityName, e.getMessage());
            return "";
        }
    }

    private static Property findColorProperty(Entity entity) {
        return entity.getProperties().stream()
                .filter(p -> !p.isOneToMany() && !p.isId() && isColorProperty(p))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns {@code true} if the property's domain type corresponds to the Color
     * AD_Reference (ID {@value Constants#COLOR_REFERENCE_ID}), or if the property
     * name is simply "color" or ends with "color".
     */
    private static boolean isColorProperty(Property p) {
        String propName = p.getName().toLowerCase();
        if (propName.equals("color") || propName.endsWith("color")) {
            return true;
        }

        DomainType dt = p.getDomainType();
        if (dt == null) {
            return false;
        }
        org.openbravo.base.model.Reference ref = dt.getReference();
        return ref != null && Constants.COLOR_REFERENCE_ID != null && Constants.COLOR_REFERENCE_ID.equals(ref.getId());
    }

}
