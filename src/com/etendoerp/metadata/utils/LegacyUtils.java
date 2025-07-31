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
    public static boolean isLegacyProcess(String processId) {
        return LEGACY_PROCESS_IDS.contains(processId);
    }

    /**
     * Simulates a legacy Process object with minimal required data.
     *
     * @param processId The ID to assign to the stubbed process
     * @return A stubbed Process instance with minimal fields set
     */
    public static Process getLegacyProcess(String processId) {
        Process legacyProcess = (Process) OBProvider.getInstance().get(Process.class);

        legacyProcess.setId(processId);
        legacyProcess.setName("Legacy Process Placeholder");
        legacyProcess.setActive(true);

        return legacyProcess;
    }
}
