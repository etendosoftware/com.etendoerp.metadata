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

import org.openbravo.base.provider.OBProvider;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBQuery;
import org.openbravo.model.ad.ui.Tab;
import org.apache.commons.lang3.StringUtils;
import java.util.List;
import java.util.Set;

/**
 * Utility class for legacy-process auxiliary helpers (stub processes, path
 * detection, mutable session attribute registry). Legacy detection itself is
 * handled by {@link com.etendoerp.metadata.builders.LegacyProcessResolver#isLegacy(
 * org.openbravo.model.ad.ui.Field)} and lives there exclusively.
 */
public class LegacyUtils {
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
}
