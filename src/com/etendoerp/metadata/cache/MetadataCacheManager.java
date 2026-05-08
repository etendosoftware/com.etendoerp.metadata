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

package com.etendoerp.metadata.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.etendoerp.metadata.builders.FieldBuilderWithColumn;
import com.etendoerp.metadata.builders.WindowBuilder;
import com.etendoerp.metadata.data.TabProcessor;

/**
 * Central entry point for invalidating all metadata caches in the module.
 * Replicates the full-reset behavior of ApplicationDictionaryCachedStructures.init()
 * from the classic interface.
 */
public class MetadataCacheManager {
  private static final Logger logger = LogManager.getLogger(MetadataCacheManager.class);

  private MetadataCacheManager() {
  }

  /**
   * Invalidates all metadata caches: field, field access, tab allowed, and window access.
   * Called by {@link MetadataCacheInvalidationObserver} when Application Dictionary entities change.
   */
  public static void invalidateAll() {
    logger.info("Invalidating all metadata caches");
    TabProcessor.clearFieldCache();
    TabProcessor.clearFieldAccessCache();
    WindowBuilder.clearTabAllowedCache();
    FieldBuilderWithColumn.clearWindowAccessCache();
  }
}
