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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.utils;

import java.util.Map;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.service.db.CallProcess;
import com.etendoerp.metadata.utils.CallAsyncProcess;

/**
 * Utility class to call processes.
 *
 */
public class ProcessExecutionUtils {

  private ProcessExecutionUtils() {
  }

  /**
   * Calls a process using the current process execution class.
   *
   * @param processName the name of the process to call
   * @param recordId the record id to pass to the process
   * @param parameters the parameters to pass to the process
   * @return the process instance
   */
  public static ProcessInstance callProcess(String processName, String recordId, Map<String, String> parameters) {
    return CallProcess.getInstance().call(processName, recordId, parameters);
  }

  /**
   * Calls a process using the current process execution class with commit control.
   *
   * @param processName the name of the process to call
   * @param recordId the record id to pass to the process
   * @param parameters the parameters to pass to the process
   * @param doCommit whether to commit the transaction
   * @return the process instance
   */
  public static ProcessInstance callProcess(String processName, String recordId, Map<String, String> parameters, Boolean doCommit) {
    return CallProcess.getInstance().call(processName, recordId, parameters, doCommit);
  }

  /**
   * Calls a process using the current process execution class.
   *
   * @param process the process to call
   * @param recordId the record id to pass to the process
   * @param parameters the parameters to pass to the process
   * @return the process instance
   */
  public static ProcessInstance callProcess(Process process, String recordId, Map<String, String> parameters) {
    return CallProcess.getInstance().call(process, recordId, parameters);
  }

  /**
   * Calls a process using the current process execution class with commit control.
   *
   * @param process the process to call
   * @param recordId the record id to pass to the process
   * @param parameters the parameters to pass to the process
   * @param doCommit whether to commit the transaction
   * @return the process instance
   */
  public static ProcessInstance callProcess(Process process, String recordId, Map<String, String> parameters, Boolean doCommit) {
    return CallProcess.getInstance().call(process, recordId, parameters, doCommit);
  }

  /**
   * Calls a process asynchronously using the current process execution class.
   *
   * @param processName the name of the process to call
   * @param recordId the record id to pass to the process
   * @param parameters the parameters to pass to the process
   * @return the process instance
   */
  public static ProcessInstance callProcessAsync(String processName, String recordId, Map<String, String> parameters) {
    return CallAsyncProcess.getInstance().call(processName, recordId, parameters);
  }

  /**
   * Calls a process asynchronously using the current process execution class with commit control.
   *
   * @param processName the name of the process to call
   * @param recordId the record id to pass to the process
   * @param parameters the parameters to pass to the process
   * @param doCommit whether to commit the transaction
   * @return the process instance
   */
  public static ProcessInstance callProcessAsync(String processName, String recordId, Map<String, String> parameters, Boolean doCommit) {
    return CallAsyncProcess.getInstance().call(processName, recordId, parameters, doCommit);
  }

  /**
   * Calls a process asynchronously using the current process execution class.
   *
   * @param process the process to call
   * @param recordId the record id to pass to the process
   * @param parameters the parameters to pass to the process
   * @return the process instance
   */
  public static ProcessInstance callProcessAsync(Process process, String recordId, Map<String, String> parameters) {
    return CallAsyncProcess.getInstance().call(process, recordId, parameters);
  }

  /**
   * Calls a process asynchronously using the current process execution class with commit control.
   *
   * @param process the process to call
   * @param recordId the record id to pass to the process
   * @param parameters the parameters to pass to the process
   * @param doCommit whether to commit the transaction
   * @return the process instance
   */
  public static ProcessInstance callProcessAsync(Process process, String recordId, Map<String, String> parameters, Boolean doCommit) {
    return CallAsyncProcess.getInstance().call(process, recordId, parameters, doCommit);
  }

}
