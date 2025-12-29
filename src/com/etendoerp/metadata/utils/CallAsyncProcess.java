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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.process.Parameter;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.service.db.CallProcess;

/**
 * Service class to execute database processes asynchronously.
 * Replicates logic from CallProcess because core cannot be modified.
 */
public class CallAsyncProcess extends CallProcess {

  private static final Logger log = LogManager.getLogger(CallAsyncProcess.class);
  public static final String PROCESSING_MSG = "Processing in background...";
  private static CallAsyncProcess instance = new CallAsyncProcess();
  private final ExecutorService executorService = Executors.newFixedThreadPool(10);

  public static synchronized CallAsyncProcess getInstance() {
    return instance;
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
        for (String key : parameters.keySet()) {
          index++;
          final Object value = parameters.get(key);
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

      // 2. ASYNC PHASE: Submit to Executor
      executorService.submit(() -> {
        runInBackground(pInstanceId, processId, contextValues, doCommit);
      });

      return pInstance;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private void runInBackground(String pInstanceId, String processId, ContextValues contextValues, Boolean doCommit) {
    try {
      hydrateContext(contextValues);
      OBContext.setAdminMode();

      ProcessInstance pInstance = OBDal.getInstance().get(ProcessInstance.class, pInstanceId);
      Process process = OBDal.getInstance().get(Process.class, processId);

      if (pInstance == null || process == null) {
        throw new OBException("Async Execution Failed: Process Instance or Definition not found.");
      }

      // Execute DB Logic (Replicated from CallProcess)
      executeProcedure(pInstance, process, doCommit);
      
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
        this.languageId = context.getLanguage().getId();
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
