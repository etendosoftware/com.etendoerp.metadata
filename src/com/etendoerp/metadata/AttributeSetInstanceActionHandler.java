package com.etendoerp.metadata;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.AttributeSetInstanceValue;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.common.plm.Attribute;
import org.openbravo.model.common.plm.AttributeInstance;
import org.openbravo.model.common.plm.AttributeSet;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.AttributeUse;
import org.openbravo.model.common.plm.AttributeValue;
import org.openbravo.service.db.DalConnectionProvider;

/**
 * Action Handler for Attribute Set Instances via JSON.
 * Supports actions via _buttonValue:
 * - "DONE" (default): creates or updates an Attribute Set Instance
 * - "FETCH": retrieves existing instance data (core fields + custom attributes)
 * - "CONFIG": retrieves the attribute set configuration (fields, custom attributes, list values)
 */
@ApplicationScoped
public class AttributeSetInstanceActionHandler extends BaseActionHandler {
  private static final Logger log = LogManager.getLogger();
  private static final String BUTTON_VALUE_FETCH = "FETCH";
  private static final String BUTTON_VALUE_CONFIG = "CONFIG";
  private static final String PARAMS = "_params";
  private static final String STATUS = "status";
  private static final String MESSAGE = "message";
  private static final String INSTANCE_ID = "instanceId";
  private static final String EXPIRATION_DATE = "expirationDate";
  private static final String STATUS_ERROR = "Error";
  private static final String STATUS_SUCCESS = "Success";
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject result = new JSONObject();
    try {
      OBContext.setAdminMode(true);
      JSONObject jsonContent = new JSONObject(content);
      String buttonValue = jsonContent.optString("_buttonValue", "DONE");

      if (BUTTON_VALUE_FETCH.equals(buttonValue)) {
        result = executeFetch(jsonContent);
      } else if (BUTTON_VALUE_CONFIG.equals(buttonValue)) {
        result = executeConfig(jsonContent);
      } else {
        result = executeSave(jsonContent);
      }
    } catch (Exception e) {
      log.error("Error in AttributeSetInstanceActionHandler", e);
      try {
        result.put(STATUS, STATUS_ERROR);
        result.put(MESSAGE, e.getMessage());
      } catch (Exception ignored) {
        log.error("Error setting error result", ignored);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return result;
  }

  private JSONObject executeConfig(JSONObject jsonContent) throws Exception {
    JSONObject result = new JSONObject();
    JSONObject params = jsonContent.getJSONObject(PARAMS);
    String attributeSetId = params.getString("attributeSetId");

    AttributeSet attrSet = OBDal.getInstance().get(AttributeSet.class, attributeSetId);
    if (attrSet == null) {
      result.put(STATUS, STATUS_ERROR);
      result.put(MESSAGE, "Attribute Set not found");
      return result;
    }

    result.put(STATUS, STATUS_SUCCESS);
    result.put("id", attrSet.getId());
    result.put("name", StringUtils.defaultString(attrSet.getName(), ""));
    result.put("isLot", attrSet.isLot());
    result.put("isSerNo", attrSet.isSerialNo());
    result.put("isExpirationDate", attrSet.isExpirationDate());
    result.put("isGuaranteeDate", attrSet.isExpirationDate() && attrSet.getGuaranteedDays() != null && attrSet.getGuaranteedDays() > 0);

    // Fetch custom attributes via AttributeUse
    JSONArray customAttributes = new JSONArray();
    OBCriteria<AttributeUse> useCriteria = OBDal.getInstance().createCriteria(AttributeUse.class);
    useCriteria.add(Restrictions.eq(AttributeUse.PROPERTY_ATTRIBUTESET, attrSet));
    useCriteria.addOrderBy(AttributeUse.PROPERTY_SEQUENCENUMBER, true);
    List<AttributeUse> uses = useCriteria.list();

    for (AttributeUse use : uses) {
      Attribute attr = use.getAttribute();
      if (attr == null) {
        continue;
      }

      JSONObject attrJson = new JSONObject();
      attrJson.put("id", attr.getId());
      attrJson.put("name", StringUtils.defaultString(attr.getName(), ""));
      attrJson.put("isList", attr.isList());
      attrJson.put("isMandatory", attr.isMandatory());
      attrJson.put("sequenceNumber", use.getSequenceNumber());

      // For list attributes, include available values
      if (attr.isList()) {
        JSONArray valuesArray = new JSONArray();
        OBCriteria<AttributeValue> valCriteria = OBDal.getInstance()
            .createCriteria(AttributeValue.class);
        valCriteria.add(Restrictions.eq(AttributeValue.PROPERTY_ATTRIBUTE, attr));
        valCriteria.addOrderBy(AttributeValue.PROPERTY_NAME, true);
        List<AttributeValue> attrValues = valCriteria.list();

        for (AttributeValue av : attrValues) {
          JSONObject valJson = new JSONObject();
          valJson.put("id", av.getId());
          valJson.put("name", StringUtils.defaultString(av.getName(), ""));
          valuesArray.put(valJson);
        }
        attrJson.put("values", valuesArray);
      }

      customAttributes.put(attrJson);
    }
    result.put("customAttributes", customAttributes);

    return result;
  }

  private JSONObject executeFetch(JSONObject jsonContent) throws Exception {
    JSONObject result = new JSONObject();
    JSONObject params = jsonContent.getJSONObject(PARAMS);
    String instanceId = params.getString(INSTANCE_ID);

    if (StringUtils.isEmpty(instanceId) || "0".equals(instanceId)) {
      result.put(STATUS, STATUS_ERROR);
      result.put(MESSAGE, "No instance ID provided");
      return result;
    }

    AttributeSetInstance asi = OBDal.getInstance().get(AttributeSetInstance.class, instanceId);
    if (asi == null) {
      result.put(STATUS, STATUS_ERROR);
      result.put(MESSAGE, "Attribute Set Instance not found");
      return result;
    }

    result.put(STATUS, STATUS_SUCCESS);
    result.put(INSTANCE_ID, asi.getId());
    result.put("description", StringUtils.defaultString(asi.getDescription(), ""));
    result.put("lotName", StringUtils.defaultString(asi.getLotName(), ""));
    result.put("serialNo", StringUtils.defaultString(asi.getSerialNo(), ""));

    if (asi.getExpirationDate() != null) {
      result.put(EXPIRATION_DATE, dateFormat.format(asi.getExpirationDate()));
    } else {
      result.put(EXPIRATION_DATE, "");
    }

    // AttributeSetInstance doesn't have a separate guaranteeDate property in the standard model.
    // The expirationDate property actually maps to the GuaranteeDate column.
    result.put("guaranteeDate", "");

    // Fetch custom AttributeInstance records linked to this ASI
    JSONObject customValues = new JSONObject();
    OBCriteria<AttributeInstance> aiCriteria = OBDal.getInstance()
        .createCriteria(AttributeInstance.class);
    aiCriteria.add(Restrictions.eq(AttributeInstance.PROPERTY_ATTRIBUTESETVALUE, asi));
    List<AttributeInstance> attrInstances = aiCriteria.list();

    log.debug("Found " + attrInstances.size() + " attribute instances for ASI " + instanceId);
    for (AttributeInstance ai : attrInstances) {
      if (ai.getAttribute() == null) {
        continue;
      }
      String attrId = ai.getAttribute().getId();
      String valueId = "";
      String identifier = "";

      if (ai.getAttributeValue() != null) {
        valueId = ai.getAttributeValue().getId();
        identifier = ai.getAttributeValue().getIdentifier();
      } else if (StringUtils.isNotEmpty(ai.getSearchKey())) {
        valueId = ai.getSearchKey();
        identifier = ai.getSearchKey();
      }

      if (StringUtils.isNotEmpty(valueId)) {
        customValues.put(attrId, valueId);
        customValues.put(attrId + "_identifier", identifier);
      }
    }
    result.put("customAttributes", customValues);

    return result;
  }

  private JSONObject executeSave(JSONObject jsonContent) throws Exception {
    JSONObject result = new JSONObject();
    JSONObject params = jsonContent.getJSONObject(PARAMS);

    String attributeSetId = params.getString("attributeSetId");
    String instanceId = params.optString(INSTANCE_ID, "");
    String lot = params.optString("lot", "");
    String serialNo = params.optString("serialNo", "");
    String expirationDate = convertToClassicDateFormat(params.optString(EXPIRATION_DATE, ""));
    String isLocked = params.optString("isLocked", "N");
    String lockDescription = params.optString("lockDescription", "");

    String windowId = params.optString("windowId", "");
    String isSOTrx = params.optString("isSOTrx", "N");
    String productId = params.optString("productId", "");

    Map<String, String> attributeValues = new HashMap<>();
    if (params.has("attributes")) {
      JSONObject attrs = params.getJSONObject("attributes");
      Iterator<String> keys = attrs.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        // Ignore identifier fields sent by frontend for display purposes
        if (key.endsWith("_identifier")) {
          continue;
        }
        
        // Map Attribute ID to its normalized Name (elementname)
        Attribute attr = OBDal.getInstance().get(Attribute.class, key);
        if (attr != null) {
          String normalizedName = replace(attr.getName());
          attributeValues.put(normalizedName, attrs.getString(key));
          log.debug("Mapping attribute " + key + " (" + attr.getName() + ") -> " + normalizedName + " = " + attrs.getString(key));
        } else {
          // Fallback if key is already a name or unknown
          attributeValues.put(key, attrs.getString(key));
        }
      }
    }

    VariablesSecureApp vars = RequestContext.get().getVariablesSecureApp();
    AttributeSetInstanceValue attSetValue = new AttributeSetInstanceValue(lot, serialNo,
        expirationDate, isLocked, lockDescription);

    ConnectionProvider conn = new DalConnectionProvider(true);
    OBError myMessage = attSetValue.setAttributeInstance(conn, vars, attributeSetId, instanceId,
        windowId, isSOTrx, productId, attributeValues);

    result.put(STATUS, myMessage.getType());
    result.put(MESSAGE, myMessage.getMessage());
    result.put(INSTANCE_ID, attSetValue.getAttSetInstanceId());

    // Clear Hibernate session to discard stale cache from the SQL-level save
    OBDal.getInstance().getSession().clear();

    String description = "";
    if (StringUtils.isNotEmpty(attSetValue.getAttSetInstanceId())) {
      AttributeSetInstance asi = OBDal.getInstance().get(AttributeSetInstance.class,
          attSetValue.getAttSetInstanceId());
      if (asi != null) {
        description = asi.getDescription();
      }
    }
    result.put("description", description);

    return result;
  }

  private static String convertToClassicDateFormat(String date) {
    if (StringUtils.isEmpty(date)) {
      return date;
    }
    if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
      String[] parts = date.split("-");
      return parts[2] + "-" + parts[1] + "-" + parts[0];
    }
    return date;
  }

  private String replace(String strIni) {
    // delete characters: " ","&",",","#","(",")"
    if (strIni == null) {
      return "";
    }
    return strIni.replaceAll("[ #&,\\(\\)]", "");
  }
}
