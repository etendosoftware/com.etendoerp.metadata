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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.Sqlc;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.process.Parameter;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.CallProcess;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Service class to execute database processes asynchronously.
 * Replicates logic from CallProcess because core cannot be modified.
 */
public class CallAsyncProcess extends CallProcess {

  private static final Logger log = LogManager.getLogger(CallAsyncProcess.class);
  public static final String PROCESSING_MSG = "Processing in background...";
  /** {@link OBError#getType()} value returned by a process that failed. */
  private static final String ERROR_TYPE = "Error";
  private static final long RESULT_SUCCESS = 1L;
  private static final long RESULT_ERROR = 0L;
  /** AD_Model_Object action that designates the process implementation class. */
  private static final String MODEL_OBJECT_PROCESS_ACTION = "P";
  private static CallAsyncProcess instance = new CallAsyncProcess();
  private ExecutorService executorService = Executors.newFixedThreadPool(10);

  public static synchronized CallAsyncProcess getInstance() {
    return instance;
  }

  /**
   * Sets the executor service to use for background processes.
   * Internal use only, primarily for testing purposes.
   *
   * @param executorService the executor service to use
   */
  public void setExecutorService(ExecutorService executorService) {
    if (executorService == null) {
      throw new OBException("ExecutorService cannot be null");
    }
    this.executorService = executorService;
  }

  /**
   * Returns the executor service used for background processes.
   *
   * @return the current executor service
   */
  public ExecutorService getExecutorService() {
    return executorService;
  }

  @Override
  public ProcessInstance callProcess(Process process, String recordID, Map<String, ?> parameters, Boolean doCommit) {
    OBContext.setAdminMode();
    try {
      // 1. SYNC PHASE: Prepare Data (Replicated from CallProcess)
      final ProcessInstance pInstance = OBProvider.getInstance().get(ProcessInstance.class);
      pInstance.setProcess(process);
      pInstance.setActive(true);
      pInstance.setAllowRead(true);

      if (recordID != null) {
        pInstance.setRecordID(recordID);
      } else {
        pInstance.setRecordID("0");
      }

      pInstance.setUserContact(OBContext.getOBContext().getUser());

      if (parameters != null) {
        int index = 0;
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
          index++;
          final String key = entry.getKey();
          final Object value = entry.getValue();
          final Parameter parameter = OBProvider.getInstance().get(Parameter.class);
          parameter.setSequenceNumber(index + "");
          parameter.setParameterName(key);
          if (value instanceof String) {
            parameter.setString((String) value);
          } else if (value instanceof Date) {
            parameter.setProcessDate((Date) value);
          } else if (value instanceof BigDecimal) {
            parameter.setProcessNumber((BigDecimal) value);
          }
          pInstance.getADParameterList().add(parameter);
          parameter.setProcessInstance(pInstance);
        }
      }

      OBDal.getInstance().save(pInstance);
      
      // Set initial status
      pInstance.setResult(0L);
      pInstance.setErrorMsg(PROCESSING_MSG);
      
      OBDal.getInstance().flush();

      final String pInstanceId = pInstance.getId();
      final String processId = process.getId();
      final ContextValues contextValues = new ContextValues(OBContext.getOBContext());
      // Captured for the Java-process branch, which reads them from the bundle
      // (the PL/SQL branch reads them from AD_PInstance_Para saved above).
      final Map<String, String> bundleParameters = toStringParameters(parameters);

      // 2. ASYNC PHASE: Submit to Executor
      executorService.submit(() ->
        runInBackground(pInstanceId, processId, contextValues, doCommit, bundleParameters)
      );

      return pInstance;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private void runInBackground(String pInstanceId, String processId, ContextValues contextValues,
      Boolean doCommit, Map<String, String> parameters) {
    try {
      hydrateContext(contextValues);
      OBContext.setAdminMode();

      ProcessInstance pInstance = OBDal.getInstance().get(ProcessInstance.class, pInstanceId);
      Process process = OBDal.getInstance().get(Process.class, processId);

      if (pInstance == null || process == null) {
        throw new OBException("Async Execution Failed: Process Instance or Definition not found.");
      }

      // Dispatch by implementation type, mirroring Classic ProcessBundle.init():
      // Java class, PL/SQL stored procedure, or none.
      executeByType(pInstance, process, contextValues, doCommit, parameters);

      OBDal.getInstance().commitAndClose();
    } catch (Exception e) {
      try {
        OBDal.getInstance().rollbackAndClose();
        OBContext.setAdminMode();
        ProcessInstance pInstanceCtx = OBDal.getInstance().get(ProcessInstance.class, pInstanceId);
        if (pInstanceCtx != null) {
          pInstanceCtx.setResult(0L);
          String msg = e.getMessage() != null ? e.getMessage() : e.toString();
          if (msg.length() > 2000) msg = msg.substring(0, 2000);
          pInstanceCtx.setErrorMsg("Async Error: " + msg);
          OBDal.getInstance().save(pInstanceCtx);
          OBDal.getInstance().commitAndClose();
        }
      } catch (Exception ex) {
        log.error("Error updating process instance status", ex);
      }
      log.error("Error in background process execution", e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Routes the execution to the right implementation, replicating Classic's
   * {@code ProcessBundle.init()} dispatch: a process backed by a Java class runs
   * through the scheduling framework, one with a stored procedure keeps the
   * PL/SQL path, and one with neither fails with a readable message instead of
   * building invalid SQL ({@code SELECT * FROM null(?)}).
   *
   * @param pInstance the process instance to update with the result
   * @param process the process definition being executed
   * @param contextValues the captured session context used to build the bundle
   * @param doCommit forwarded to the PL/SQL procedure when applicable
   * @param parameters the process parameters keyed by DB column name
   * @throws Exception if the Java process or the PL/SQL procedure execution fails
   */
  private void executeByType(ProcessInstance pInstance, Process process, ContextValues contextValues,
      Boolean doCommit, Map<String, String> parameters) throws Exception {
    if (hasJavaClass(process)) {
      executeJavaProcess(pInstance, process, contextValues, parameters);
    } else if (hasProcedure(process)) {
      executeProcedure(pInstance, process, doCommit);
    } else {
      failNoImplementation(pInstance, process);
    }
  }

  /**
   * Returns true when the process resolves to a Java class implementation.
   *
   * @param process the process definition
   * @return true if a Java class is configured
   */
  private boolean hasJavaClass(Process process) {
    return resolveJavaClassName(process) != null;
  }

  /**
   * Resolves the Java class name the way Classic does, mirroring
   * {@code COALESCE(AD_Process.classname, AD_Model_Object.classname)}: the process'
   * own class name takes precedence, otherwise the class of its process-action
   * ({@code 'P'}) model object.
   *
   * @param process the process definition
   * @return the Java class name, or null when the process has none
   */
  private String resolveJavaClassName(Process process) {
    String fromProcess = process.getJavaClassName();
    if (fromProcess != null && !fromProcess.isEmpty()) {
      return fromProcess;
    }
    for (ModelImplementation modelObject : process.getADModelImplementationList()) {
      if (MODEL_OBJECT_PROCESS_ACTION.equals(modelObject.getAction())) {
        String className = modelObject.getJavaClassName();
        if (className != null && !className.isEmpty()) {
          return className;
        }
      }
    }
    return null;
  }

  /**
   * Returns true when the process defines a non-empty stored procedure name.
   *
   * @param process the process definition
   * @return true if a stored procedure is configured
   */
  private boolean hasProcedure(Process process) {
    String procedure = process.getProcedure();
    return procedure != null && !procedure.isEmpty();
  }

  /**
   * Runs a Java process the way Classic's {@code DefaultJob} does: it resolves the
   * bundle (so its process class is known), instantiates the class by reflection
   * (legacy processes such as those in {@code org.openbravo.service.db} are excluded
   * from CDI scanning, so Weld cannot resolve them) and calls {@code execute}. The
   * resulting {@link OBError} is written back to the process instance so the status
   * endpoint can report it. The {@link ProcessBundle} is built only here (not on the
   * PL/SQL path) so its DB resolution stays out of the stored-procedure branch.
   *
   * @param pInstance the process instance to update with the result
   * @param process the process definition being executed
   * @param contextValues the captured session context used to build the bundle
   * @param parameters the process parameters keyed by DB column name
   * @throws Exception if the bundle cannot be built or the process execution fails
   */
  private void executeJavaProcess(ProcessInstance pInstance, Process process, ContextValues contextValues,
      Map<String, String> parameters) throws Exception {
    VariablesSecureApp vars = new VariablesSecureApp(contextValues.userId, contextValues.clientId,
        contextValues.orgId, contextValues.roleId, contextValues.languageId);
    ConnectionProvider connectionProvider = new DalConnectionProvider(false);
    ProcessBundle bundle = new ProcessBundle(process.getId(), vars).init(connectionProvider);
    populateBundleParams(bundle, parameters);

    org.openbravo.scheduling.Process processInstance = bundle.getProcessClass()
        .getDeclaredConstructor()
        .newInstance();
    processInstance.execute(bundle);
    applyResultToInstance(pInstance, bundle.getResult());
  }

  /**
   * Populates the bundle params using the same column-name to key transformation
   * Classic's generated servlets use ({@code Sqlc.TransformaNombreColumna}), so
   * each Java process finds the keys it expects (e.g. AD_Client_ID -> adClientId).
   *
   * @param bundle the bundle whose params map is filled
   * @param parameters the process parameters keyed by DB column name
   */
  private void populateBundleParams(ProcessBundle bundle, Map<String, String> parameters) {
    if (parameters == null) {
      return;
    }
    Map<String, Object> bundleParams = bundle.getParams();
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      bundleParams.put(Sqlc.TransformaNombreColumna(entry.getKey()), entry.getValue());
    }
  }

  /**
   * Maps the {@link OBError} produced by a Java process onto the process instance
   * result code and message. A missing result is treated as success.
   *
   * @param pInstance the process instance to update
   * @param result the bundle result (expected to be an {@link OBError})
   */
  private void applyResultToInstance(ProcessInstance pInstance, Object result) {
    if (result instanceof OBError) {
      OBError error = (OBError) result;
      boolean isError = ERROR_TYPE.equalsIgnoreCase(error.getType());
      pInstance.setResult(isError ? RESULT_ERROR : RESULT_SUCCESS);
      pInstance.setErrorMsg(error.getMessage());
    } else {
      pInstance.setResult(RESULT_SUCCESS);
      pInstance.setErrorMsg(null);
    }
    OBDal.getInstance().save(pInstance);
  }

  /**
   * Marks the process instance as failed when the process has neither a stored
   * procedure nor a Java class, avoiding the invalid {@code SELECT * FROM null(?)}.
   *
   * @param pInstance the process instance to update
   * @param process the process definition without an implementation
   */
  private void failNoImplementation(ProcessInstance pInstance, Process process) {
    pInstance.setResult(RESULT_ERROR);
    pInstance.setErrorMsg("Process '" + process.getName()
        + "' has no stored procedure or Java class to execute.");
    OBDal.getInstance().save(pInstance);
  }

  /**
   * Converts the raw parameter map into a string-valued map for the Java-process
   * bundle, skipping null values.
   *
   * @param parameters the raw parameters (values may be String, Date, BigDecimal)
   * @return a map of column name to string value
   */
  private Map<String, String> toStringParameters(Map<String, ?> parameters) {
    Map<String, String> result = new HashMap<>();
    if (parameters == null) {
      return result;
    }
    for (Map.Entry<String, ?> entry : parameters.entrySet()) {
      if (entry.getValue() != null) {
        result.put(entry.getKey(), String.valueOf(entry.getValue()));
      }
    }
    return result;
  }

  private void executeProcedure(ProcessInstance pInstance, Process process, Boolean doCommit) throws SQLException {
    PreparedStatement ps = null;
    try {
      final Connection connection = OBDal.getInstance().getConnection(false);

      String procedureParameters = "(?)";
      if (doCommit != null) {
        procedureParameters = "(?,?)";
      }

      final Properties obProps = OBPropertiesProvider.getInstance().getOpenbravoProperties();
      if (obProps.getProperty("bbdd.rdbms") != null
          && obProps.getProperty("bbdd.rdbms").equals("POSTGRE")) {
        ps = connection.prepareStatement("SELECT * FROM " + process.getProcedure() + procedureParameters);
      } else {
        ps = connection.prepareStatement(" CALL " + process.getProcedure() + procedureParameters);
      }

      ps.setString(1, pInstance.getId());
      if (doCommit != null) {
        ps.setString(2, doCommit ? "Y" : "N");
      }
      ps.execute();
      
      // Refresh to get results from SP
      OBDal.getInstance().getSession().refresh(pInstance);
    } finally {
      if (ps != null) {
        try {
          ps.close();
        } catch (SQLException e) {
          // ignore
        }
      }
    }
  }

  /**
   * Helper class to store context values for hydration.
   */
  private static class ContextValues {
    String userId;
    String roleId;
    String clientId;
    String orgId;
    String languageId;
    String warehouseId;

    ContextValues(OBContext context) {
      if (context != null) {
        this.userId = context.getUser().getId();
        this.roleId = context.getRole().getId();
        this.clientId = context.getCurrentClient().getId();
        this.orgId = context.getCurrentOrganization().getId();
        this.languageId = context.getLanguage().getLanguage();
        if (context.getWarehouse() != null) {
          this.warehouseId = context.getWarehouse().getId();
        }
      }
    }
  }

  private void hydrateContext(ContextValues values) {
    if (values != null && values.userId != null) {
      OBContext.setOBContext(values.userId, values.roleId, values.clientId, values.orgId, values.languageId, values.warehouseId);
    }
  }
}
