package com.etendoerp.metadata;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.AttributeSetInstanceValue;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Action Handler to create or update Attribute Set Instances via JSON.
 * Replicates the logic from AttributeSetInstance.java servlet but in a stateless-friendly way.
 */
@ApplicationScoped
public class AttributeSetInstanceActionHandler extends BaseActionHandler {
  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject result = new JSONObject();
    try {
      OBContext.setAdminMode(true);
      JSONObject jsonContent = new JSONObject(content);
      JSONObject params = jsonContent.getJSONObject("_params");
      
      String attributeSetId = params.getString("attributeSetId");
      String instanceId = params.optString("instanceId", "");
      String lot = params.optString("lot", "");
      String serialNo = params.optString("serialNo", "");
      String expirationDate = params.optString("expirationDate", "");
      String isLocked = params.optString("isLocked", "N");
      String lockDescription = params.optString("lockDescription", "");
      
      // Contextual parameters required by AttributeSetInstanceValue
      String windowId = params.optString("windowId", "");
      String isSOTrx = params.optString("isSOTrx", "N");
      String productId = params.optString("productId", "");

      // Custom attributes
      Map<String, String> attributeValues = new HashMap<>();
      if (params.has("attributes")) {
        JSONObject attrs = params.getJSONObject("attributes");
        Iterator<String> keys = attrs.keys();
        while (keys.hasNext()) {
          String key = keys.next();
          attributeValues.put(key, attrs.getString(key));
        }
      }

      VariablesSecureApp vars = RequestContext.get().getVariablesSecureApp();
      AttributeSetInstanceValue attSetValue = new AttributeSetInstanceValue(lot, serialNo, expirationDate, isLocked, lockDescription);
      
      ConnectionProvider conn = new DalConnectionProvider(true);
      OBError myMessage = attSetValue.setAttributeInstance(conn, vars, attributeSetId, instanceId, windowId, isSOTrx, productId, attributeValues);

      result.put("status", myMessage.getType());
      result.put("message", myMessage.getMessage());
      result.put("instanceId", attSetValue.getAttSetInstanceId());
      
      // Fetch updated description
      String description = "";
      if (StringUtils.isNotEmpty(attSetValue.getAttSetInstanceId())) {
        org.openbravo.model.common.plm.AttributeSetInstance asi = OBDal.getInstance().get(org.openbravo.model.common.plm.AttributeSetInstance.class, attSetValue.getAttSetInstanceId());
        if (asi != null) {
          description = asi.getDescription();
        }
      }
      result.put("description", description);

    } catch (Exception e) {
      log.error("Error saving Attribute Set Instance", e);
      try {
        result.put("status", "Error");
        result.put("message", e.getMessage());
      } catch (Exception ignored) {}
    } finally {
      OBContext.restorePreviousMode();
    }
    return result;
  }
}
