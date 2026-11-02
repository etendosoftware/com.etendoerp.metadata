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

package com.etendoerp.metadata.data;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.businessUtility.Preferences;
import org.openbravo.erpCommon.utility.PropertyException;
import org.openbravo.model.ad.access.ProcessAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Window;

import com.etendoerp.metadata.utils.Constants;

/**
 * Tells whether the current role can run the process of a button field, with the same rules
 * Classic uses to disable buttons ({@code WindowSettingsActionHandler.getNotAccessibleProcesses}).
 */
public final class ProcessAccessChecker {

  private ProcessAccessChecker() {
  }

  /**
   * Checks the process linked to the field's column:
   * <ul>
   * <li>Process Definition: {@link TabProcessor#hasAccessToProcess}, i.e.
   * {@code BaseProcessActionHandler.hasAccess};</li>
   * <li>classic process: an active Process Access row is required when the process requires an
   * explicit access permission or the {@code SecuredProcess} preference is set;</li>
   * <li>no process (including legacy buttons): always accessible.</li>
   * </ul>
   *
   * @param field the button field to check
   * @return {@code true} if the role can run the field's process
   */
  public static boolean isProcessAccessible(Field field) {
    Column column = field.getColumn();
    if (column == null) {
      return true;
    }
    Window window = field.getTab().getWindow();
    if (column.getOBUIAPPProcess() != null) {
      return TabProcessor.hasAccessToProcess(field, window.getId());
    }
    Process process = column.getProcess();
    if (process == null) {
      return true;
    }
    return !requiresExplicitAccess(process, window) || hasActiveProcessAccess(process);
  }

  private static boolean requiresExplicitAccess(Process process, Window window) {
    return Boolean.TRUE.equals(process.isRequiresExplicitAccessPermission()) || isSecuredProcess(window);
  }

  private static boolean isSecuredProcess(Window window) {
    OBContext context = OBContext.getOBContext();
    try {
      return Preferences.YES.equals(Preferences.getPreferenceValue(Constants.SECURED_PROCESS_PREFERENCE, true,
          context.getCurrentClient(), context.getCurrentOrganization(), context.getUser(), context.getRole(),
          window));
    } catch (PropertyException e) {
      // The preference is not set: processes inherit the window access.
      return false;
    }
  }

  private static boolean hasActiveProcessAccess(Process process) {
    return OBDal.getReadOnlyInstance().createCriteria(ProcessAccess.class) //
        .add(Restrictions.eq(ProcessAccess.PROPERTY_PROCESS, process)) //
        .add(Restrictions.eq(ProcessAccess.PROPERTY_ROLE, OBContext.getOBContext().getRole())) //
        .add(Restrictions.eq(ProcessAccess.PROPERTY_ACTIVE, true)) //
        .setMaxResults(1) //
        .uniqueResult() != null;
  }
}
