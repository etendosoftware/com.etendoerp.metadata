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

package com.etendoerp.metadata.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.ad.ui.Tab;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * Utility class for legacy-process auxiliary helpers (stub processes, path
 * detection, mutable session attribute registry). Legacy detection itself is
 * handled by {@link com.etendoerp.metadata.builders.LegacyProcessResolver#isLegacy(
 * org.openbravo.model.ad.ui.Field)} and lives there exclusively.
 */
public class LegacyUtils {
    private static final Logger log = LogManager.getLogger();

    /** Set of legacy paths used in the system. */
    private static final Set<String> LEGACY_PATHS = Set.of(
            LegacyPaths.USED_BY_LINK);

    private static final Set<String> MUTABLE_SESSION_ATTRIBUTES = Set.of(
            "143|C_ORDER_ID",
            "CREATEFROM|TABID"
    );

    /**
     * Creates a stub {@link Process} with the given {@code fieldId} as its ID.
     * Used when a legacy process definition cannot be resolved from the
     * Application Dictionary.
     *
     * @param fieldId the ID to assign to the stub process
     * @return a new {@link Process} instance with the given id, a fixed placeholder
     *         name, and active set to {@code true}
     */
    public static Process getLegacyProcess(String fieldId) {
        Process legacyProcess = (Process) OBProvider.getInstance().get(Process.class);

        legacyProcess.setId(fieldId);
        legacyProcess.setName("Legacy Process Placeholder");
        legacyProcess.setActive(true);

        return legacyProcess;
    }

    /**
     * Checks if the provided path is considered a legacy path.
     *
     * @param path The request path to check
     * @return true if the path is part of the legacy paths; false otherwise
     */
    public static boolean isLegacyPath(String path) {
        return LEGACY_PATHS.contains(path);
    }

    /**
     * Checks if the provided session attribute is mutable in legacy processes.
     *
     * @param attribute The session attribute to check
     * @return true if the attribute is mutable; false otherwise
     */
    public static boolean isMutableSessionAttribute(String attribute) {
        return MUTABLE_SESSION_ATTRIBUTES.contains(attribute);
    }

    /**
     * Finds the ID of the first active tab that belongs to the given window and table.
     * The result is ordered by sequence number to ensure deterministic output when
     * multiple tabs reference the same window and table combination.
     *
     * @param windowId the ID of the AD_Window to filter by
     * @param tableId  the ID of the AD_Table to filter by
     * @return the ID of the matching tab, or {@code null} if either parameter is blank
     *         or no active tab is found for the given window and table
     */
    public static String findTabIdByWindowAndTable(String windowId, String tableId) {
        if (StringUtils.isEmpty(windowId) || StringUtils.isEmpty(tableId)) {
            return null;
        }
        OBQuery<Tab> query = OBDal.getInstance().createQuery(
            Tab.class,
            "where window.id = :windowId and table.id = :tableId and active = true order by sequenceNumber"
        );
        query.setNamedParameter("windowId", windowId);
        query.setNamedParameter("tableId", tableId);
        query.setMaxResult(1);
        List<Tab> tabs = query.list();
        return tabs.isEmpty() ? null : tabs.get(0).getId();
    }

    /**
     * Resolves the DB column name of the given entity's single id property, as needed to
     * build the {@code windowId + "|" + columnName} session-attribute key that legacy
     * UsedByLink-style lookups fall back to. Entities with zero, multiple, or unresolvable
     * id properties are not supported by that legacy convention, so this returns {@code null}
     * for them instead of the column name.
     *
     * @param entityName the DAL entity name to resolve (e.g. {@code "Order"})
     * @return the id column name, or {@code null} if the entity or its single id property
     *         could not be resolved
     */
    public static String resolveSingleIdColumnName(String entityName) {
        Entity entity = ModelProvider.getInstance().getEntity(entityName);
        if (entity == null) {
            log.warn("Entity '{}' not found in ModelProvider, cannot resolve id column", entityName);
            return null;
        }

        List<Property> idProps = entity.getIdProperties();
        if (idProps == null || idProps.size() != 1) {
            log.warn("Expected exactly one ID property for entity '{}', got {}", entityName, idProps);
            return null;
        }

        return idProps.get(0).getColumnName();
    }

    /**
     * The {@code windowId}/{@code columnName}/{@code recordId} triple needed to build the
     * legacy {@code windowId + "|" + columnName} session-attribute key that UsedByLink-style
     * lookups fall back to.
     */
    public record UsedByLinkSessionKey(String windowId, String columnName, String recordId) {
    }

    /**
     * Resolves the {@code windowId}/{@code columnName}/{@code recordId} triple for a legacy
     * UsedByLink.html session key from a request carrying {@code windowId}/{@code entityName}/
     * {@code recordId} parameters (WorkspaceUI's JSON call shape, as opposed to the legacy
     * inp-prefixed params UsedByLink.html itself expects). Centralizes the path/param
     * validation shared by every caller that needs to populate this session fallback, so each
     * caller only has to decide how to key and scope the resulting session attribute.
     *
     * @param req  the request, expected to carry windowId/entityName/recordId when path is UsedByLink
     * @param path the resolved legacy path being dispatched
     * @return the resolved key, or {@code null} if the path isn't UsedByLink.html, a required
     *         parameter is missing, or the entity's id column couldn't be resolved
     */
    public static UsedByLinkSessionKey resolveUsedByLinkSessionKey(HttpServletRequest req, String path) {
        if (!LegacyPaths.USED_BY_LINK.equals(path)) {
            return null;
        }

        String windowId = req.getParameter("windowId");
        String entityName = req.getParameter("entityName");
        String recordId = req.getParameter("recordId");

        if (windowId == null || entityName == null || recordId == null) {
            return null;
        }

        String columnName = resolveSingleIdColumnName(entityName);
        if (columnName == null) {
            return null;
        }

        return new UsedByLinkSessionKey(windowId, columnName, recordId);
    }
}
