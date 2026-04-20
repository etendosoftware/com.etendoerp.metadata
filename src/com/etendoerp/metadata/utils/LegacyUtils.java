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
import java.util.Set;

/**
 * Utility class for identifying legacy process in the system.
 * These legacy processes are identified by their unique process IDs.
 */
public class LegacyUtils {

    /**
     * @deprecated Use {@link com.etendoerp.metadata.builders.LegacyProcessResolver#isLegacy(org.openbravo.model.ad.ui.Field)}
     * instead. Detection is now column-name based and covers all legacy processes
     * without requiring manual registration of field IDs.
     */
    @Deprecated
    private static final Set<String> LEGACY_PROCESS_IDS = Set.of(
            "3663",
            "4242",
            "3670",
            "4248",
            "7C541AC0C767FDD7E040007F01016B4D"
    );

    /** Set of legacy paths used in the system. */
    private static final Set<String> LEGACY_PATHS = Set.of(
            LegacyPaths.USED_BY_LINK
    );

    private static final Set<String> MUTABLE_SESSION_ATTRIBUTES = Set.of(
            "143|C_ORDER_ID",
            "CREATEFROM|TABID"
    );


    /**
     * @deprecated Use {@link com.etendoerp.metadata.builders.LegacyProcessResolver#isLegacy(org.openbravo.model.ad.ui.Field)}
     * instead. This method relies on a hardcoded set of field IDs and is no longer
     * the primary detection mechanism.
     */
    @Deprecated
    public static boolean isLegacyProcess(String processId) {
        if (processId == null) {
            return false;
        }
        return LEGACY_PROCESS_IDS.contains(processId);
    }

    /**
     * Creates a minimal stub {@link Process} for fields that have no {@code AD_Process_ID}
     * (e.g. Posted, CreateFrom). The stub is only used so that
     * {@link com.etendoerp.metadata.builders.ProcessActionBuilder} can serialize
     * basic metadata (fieldId, columnId) and attach the resolved legacy params.
     *
     * @param fieldId the ID of the field, used as the stub process ID
     * @return a minimal stub Process instance
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
}
