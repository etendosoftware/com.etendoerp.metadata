package com.etendoerp.metadata.utils;

import java.util.Set;

/**
 * Utility class for identifying legacy process in the system.
 * These legacy processes are identified by their unique process IDs.
 */
public class LegacyUtils {

    /**
     * Set of process IDs that do not have an associated AD_Process_ID or EM_OBUIAPP_Process_ID,
     * and therefore must be treated in a special way by the system.
     */
    private static final Set<String> LEGACY_PROCESS_IDS = Set.of(
            "3663",
            "4242"
    );

    /**
     * Checks if the provided process ID belongs to the list of legacy-defined processes.
     *
     * @param processId The ID of the process definition to check
     * @return true if the ID is part of the legacy process definitions; false otherwise
     */
    public static Boolean isLegacyProcess(String processId) {
        return LEGACY_PROCESS_IDS.contains(processId);
    }
}
