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
 * Utility class for legacy-process auxiliary helpers (stub processes, path
 * detection, mutable session attribute registry). Legacy detection itself is
 * handled by {@link com.etendoerp.metadata.builders.LegacyProcessResolver#isLegacy(
 * org.openbravo.model.ad.ui.Field)} and lives there exclusively.
 */
public class LegacyUtils {

    /** Set of legacy paths used in the system. */
    private static final Set<String> LEGACY_PATHS = Set.of(
            LegacyPaths.USED_BY_LINK
    );

    private static final Set<String> MUTABLE_SESSION_ATTRIBUTES = Set.of(
            "143|C_ORDER_ID",
            "CREATEFROM|TABID"
    );


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
