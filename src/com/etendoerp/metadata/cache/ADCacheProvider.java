package com.etendoerp.metadata.cache;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.weld.WeldUtils;
import org.openbravo.client.application.window.ApplicationDictionaryCachedStructures;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;

/**
 * Read-only static accessor for Classic's ApplicationDictionaryCachedStructures bean.
 * Provides pre-initialized AD entities (Window, Tab, Field, Column, Table) from the
 * application-scoped cache, avoiding per-request database loads.
 * <p>
 * Returns null when the Weld container is not available (e.g. in unit tests)
 * or when ADCS itself returns null (e.g. modules in development mode, where
 * ADCS falls back to OBDal internally).
 * <p>
 * This class does NOT manage the ADCS lifecycle. ADCS is owned by Classic
 * and manages its own invalidation.
 */
public class ADCacheProvider {

    private static final Logger logger = LogManager.getLogger(ADCacheProvider.class);

    private ADCacheProvider() {
    }

    private static ApplicationDictionaryCachedStructures getADCS() {
        try {
            return WeldUtils.getInstanceFromStaticBeanManager(
                    ApplicationDictionaryCachedStructures.class);
        } catch (Exception e) {
            logger.debug("ADCS not available: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns a fully initialized Window from the cache, or null if unavailable.
     */
    public static Window getWindow(String windowId) {
        ApplicationDictionaryCachedStructures adcs = getADCS();
        return adcs != null ? adcs.getWindow(windowId) : null;
    }

    /**
     * Returns a fully initialized Tab from the cache, or null if unavailable.
     */
    public static Tab getTab(String tabId) {
        ApplicationDictionaryCachedStructures adcs = getADCS();
        return adcs != null ? adcs.getTab(tabId) : null;
    }

    /**
     * Returns the fully initialized fields for a tab from the cache,
     * or null if unavailable.
     */
    public static List<Field> getFieldsOfTab(Tab tab) {
        ApplicationDictionaryCachedStructures adcs = getADCS();
        return adcs != null ? adcs.getFieldsOfTab(tab) : null;
    }

    /**
     * Returns a fully initialized Table from the cache, or null if unavailable.
     */
    public static Table getTable(String tableId) {
        ApplicationDictionaryCachedStructures adcs = getADCS();
        return adcs != null ? adcs.getTable(tableId) : null;
    }
}
