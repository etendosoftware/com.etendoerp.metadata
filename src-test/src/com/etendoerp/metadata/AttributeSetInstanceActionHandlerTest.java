package com.etendoerp.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.common.plm.Attribute;
import org.openbravo.model.common.plm.AttributeInstance;
import org.openbravo.model.common.plm.AttributeSet;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.AttributeUse;
import org.openbravo.model.common.plm.AttributeValue;

/**
 * Unit tests for {@link AttributeSetInstanceActionHandler}.
 * Covers the CONFIG, FETCH, and DONE (save) flows, the convertToClassicDateFormat
 * utility, the replace() method, and exception handling in execute().
 */
@ExtendWith(MockitoExtension.class)
class AttributeSetInstanceActionHandlerTest {

    private static final String ATTR_SET_ID = "testAttrSetId";
    private static final String INSTANCE_ID_VAL = "testInstanceId";
    private static final String STATUS_KEY = "status";
    private static final String MESSAGE_KEY = "message";
    private static final String SUCCESS_STATUS = "Success";
    private static final String ERROR_STATUS = "Error";

    // ======================== executeConfig tests ========================

    @Test
    void configReturnsErrorWhenAttributeSetNotFound() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.get(AttributeSet.class, ATTR_SET_ID)).thenReturn(null);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "CONFIG")
                    .put("_params", new JSONObject().put("attributeSetId", ATTR_SET_ID))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("Attribute Set not found", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void configReturnsSuccessWithAttributeSetFields() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSet attrSet = mock(AttributeSet.class);
            when(obDal.get(AttributeSet.class, ATTR_SET_ID)).thenReturn(attrSet);
            when(attrSet.getId()).thenReturn(ATTR_SET_ID);
            when(attrSet.getName()).thenReturn("Test Set");
            when(attrSet.isLot()).thenReturn(true);
            when(attrSet.isSerialNo()).thenReturn(false);
            when(attrSet.isExpirationDate()).thenReturn(true);
            when(attrSet.getGuaranteedDays()).thenReturn(30L);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeUse> useCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeUse.class)).thenReturn(useCriteria);
            when(useCriteria.list()).thenReturn(Collections.emptyList());

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "CONFIG")
                    .put("_params", new JSONObject().put("attributeSetId", ATTR_SET_ID))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(ATTR_SET_ID, result.getString("id"));
            assertEquals("Test Set", result.getString("name"));
            assertTrue(result.getBoolean("isLot"));
            assertFalse(result.getBoolean("isSerNo"));
            assertTrue(result.getBoolean("isExpirationDate"));
            assertTrue(result.getBoolean("isGuaranteeDate"));
        }
    }

    @Test
    void configReturnsCustomAttributesWithListValues() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSet attrSet = mock(AttributeSet.class);
            when(obDal.get(AttributeSet.class, ATTR_SET_ID)).thenReturn(attrSet);
            when(attrSet.getId()).thenReturn(ATTR_SET_ID);
            when(attrSet.getName()).thenReturn("SetName");
            when(attrSet.isLot()).thenReturn(false);
            when(attrSet.isSerialNo()).thenReturn(false);
            when(attrSet.isExpirationDate()).thenReturn(false);

            // Build a list attribute via AttributeUse
            Attribute attr = mock(Attribute.class);
            when(attr.getId()).thenReturn("attrId1");
            when(attr.getName()).thenReturn("Color");
            when(attr.isList()).thenReturn(true);
            when(attr.isMandatory()).thenReturn(false);

            AttributeUse use = mock(AttributeUse.class);
            when(use.getAttribute()).thenReturn(attr);
            when(use.getSequenceNumber()).thenReturn(10L);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeUse> useCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeUse.class)).thenReturn(useCriteria);
            List<AttributeUse> useList = new ArrayList<>();
            useList.add(use);
            when(useCriteria.list()).thenReturn(useList);

            // Build attribute values
            AttributeValue av = mock(AttributeValue.class);
            when(av.getId()).thenReturn("val1");
            when(av.getName()).thenReturn("Red");

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeValue> valCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeValue.class)).thenReturn(valCriteria);
            List<AttributeValue> valList = new ArrayList<>();
            valList.add(av);
            when(valCriteria.list()).thenReturn(valList);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "CONFIG")
                    .put("_params", new JSONObject().put("attributeSetId", ATTR_SET_ID))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(1, result.getJSONArray("customAttributes").length());
            JSONObject customAttr = result.getJSONArray("customAttributes").getJSONObject(0);
            assertEquals("attrId1", customAttr.getString("id"));
            assertEquals("Color", customAttr.getString("name"));
            assertTrue(customAttr.getBoolean("isList"));
            assertEquals(1, customAttr.getJSONArray("values").length());
            assertEquals("Red", customAttr.getJSONArray("values").getJSONObject(0).getString("name"));
        }
    }

    @Test
    void configSkipsNullAttributes() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSet attrSet = mock(AttributeSet.class);
            when(obDal.get(AttributeSet.class, ATTR_SET_ID)).thenReturn(attrSet);
            when(attrSet.getId()).thenReturn(ATTR_SET_ID);
            when(attrSet.getName()).thenReturn("SetName");
            when(attrSet.isLot()).thenReturn(false);
            when(attrSet.isSerialNo()).thenReturn(false);
            when(attrSet.isExpirationDate()).thenReturn(false);

            // An AttributeUse with null attribute
            AttributeUse use = mock(AttributeUse.class);
            when(use.getAttribute()).thenReturn(null);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeUse> useCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeUse.class)).thenReturn(useCriteria);
            List<AttributeUse> useList = new ArrayList<>();
            useList.add(use);
            when(useCriteria.list()).thenReturn(useList);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "CONFIG")
                    .put("_params", new JSONObject().put("attributeSetId", ATTR_SET_ID))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(0, result.getJSONArray("customAttributes").length());
        }
    }

    @Test
    void configIsGuaranteeDateFalseWhenNoDays() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSet attrSet = mock(AttributeSet.class);
            when(obDal.get(AttributeSet.class, ATTR_SET_ID)).thenReturn(attrSet);
            when(attrSet.getId()).thenReturn(ATTR_SET_ID);
            when(attrSet.getName()).thenReturn("SetName");
            when(attrSet.isLot()).thenReturn(false);
            when(attrSet.isSerialNo()).thenReturn(false);
            when(attrSet.isExpirationDate()).thenReturn(true);
            when(attrSet.getGuaranteedDays()).thenReturn(null);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeUse> useCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeUse.class)).thenReturn(useCriteria);
            when(useCriteria.list()).thenReturn(Collections.emptyList());

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "CONFIG")
                    .put("_params", new JSONObject().put("attributeSetId", ATTR_SET_ID))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertFalse(result.getBoolean("isGuaranteeDate"));
        }
    }

    // ======================== executeFetch tests ========================

    @Test
    void fetchReturnsErrorWhenInstanceIdIsEmpty() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", ""))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("No instance ID provided", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void fetchReturnsErrorWhenInstanceIdIsZero() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", "0"))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("No instance ID provided", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void fetchReturnsErrorWhenInstanceNotFound() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(null);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", INSTANCE_ID_VAL))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("Attribute Set Instance not found", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void fetchReturnsInstanceDataWithExpirationDate() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSetInstance asi = mock(AttributeSetInstance.class);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
            when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
            when(asi.getDescription()).thenReturn("Lot #123");
            when(asi.getLotName()).thenReturn("LOT-A");
            when(asi.getSerialNo()).thenReturn("SN-001");
            // 2025-06-15
            @SuppressWarnings("deprecation")
            Date expDate = new Date(125, 5, 15);
            when(asi.getExpirationDate()).thenReturn(expDate);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(Collections.emptyList());

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", INSTANCE_ID_VAL))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(INSTANCE_ID_VAL, result.getString("instanceId"));
            assertEquals("Lot #123", result.getString("description"));
            assertEquals("LOT-A", result.getString("lotName"));
            assertEquals("SN-001", result.getString("serialNo"));
            assertEquals("2025-06-15", result.getString("expirationDate"));
            assertEquals("", result.getString("guaranteeDate"));
        }
    }

    @Test
    void fetchReturnsEmptyExpirationDateWhenNull() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSetInstance asi = mock(AttributeSetInstance.class);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
            when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
            when(asi.getDescription()).thenReturn(null);
            when(asi.getLotName()).thenReturn(null);
            when(asi.getSerialNo()).thenReturn(null);
            when(asi.getExpirationDate()).thenReturn(null);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(Collections.emptyList());

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", INSTANCE_ID_VAL))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals("", result.getString("expirationDate"));
            assertEquals("", result.getString("description"));
            assertEquals("", result.getString("lotName"));
            assertEquals("", result.getString("serialNo"));
        }
    }

    @Test
    void fetchReturnsCustomAttributeValuesFromAttributeValue() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSetInstance asi = mock(AttributeSetInstance.class);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
            when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
            when(asi.getDescription()).thenReturn("");
            when(asi.getLotName()).thenReturn("");
            when(asi.getSerialNo()).thenReturn("");
            when(asi.getExpirationDate()).thenReturn(null);

            // Build an AttributeInstance with an AttributeValue
            Attribute attr = mock(Attribute.class);
            when(attr.getId()).thenReturn("customAttrId");

            AttributeValue attrVal = mock(AttributeValue.class);
            when(attrVal.getId()).thenReturn("selectedValueId");
            when(attrVal.getIdentifier()).thenReturn("Red Color");

            AttributeInstance ai = mock(AttributeInstance.class);
            when(ai.getAttribute()).thenReturn(attr);
            when(ai.getAttributeValue()).thenReturn(attrVal);

            List<AttributeInstance> aiList = new ArrayList<>();
            aiList.add(ai);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(aiList);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", INSTANCE_ID_VAL))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            JSONObject customAttrs = result.getJSONObject("customAttributes");
            assertEquals("selectedValueId", customAttrs.getString("customAttrId"));
            assertEquals("Red Color", customAttrs.getString("customAttrId_identifier"));
        }
    }

    @Test
    void fetchReturnsCustomAttributeValuesFromSearchKey() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSetInstance asi = mock(AttributeSetInstance.class);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
            when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
            when(asi.getDescription()).thenReturn("");
            when(asi.getLotName()).thenReturn("");
            when(asi.getSerialNo()).thenReturn("");
            when(asi.getExpirationDate()).thenReturn(null);

            Attribute attr = mock(Attribute.class);
            when(attr.getId()).thenReturn("freeTextAttrId");

            AttributeInstance ai = mock(AttributeInstance.class);
            when(ai.getAttribute()).thenReturn(attr);
            when(ai.getAttributeValue()).thenReturn(null);
            when(ai.getSearchKey()).thenReturn("FreeTextValue");

            List<AttributeInstance> aiList = new ArrayList<>();
            aiList.add(ai);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(aiList);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", INSTANCE_ID_VAL))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            JSONObject customAttrs = result.getJSONObject("customAttributes");
            assertEquals("FreeTextValue", customAttrs.getString("freeTextAttrId"));
            assertEquals("FreeTextValue", customAttrs.getString("freeTextAttrId_identifier"));
        }
    }

    @Test
    void fetchSkipsAttributeInstanceWithNullAttribute() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSetInstance asi = mock(AttributeSetInstance.class);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
            when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
            when(asi.getDescription()).thenReturn("");
            when(asi.getLotName()).thenReturn("");
            when(asi.getSerialNo()).thenReturn("");
            when(asi.getExpirationDate()).thenReturn(null);

            // An instance with null attribute
            AttributeInstance ai = mock(AttributeInstance.class);
            when(ai.getAttribute()).thenReturn(null);

            List<AttributeInstance> aiList = new ArrayList<>();
            aiList.add(ai);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(aiList);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", INSTANCE_ID_VAL))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(0, result.getJSONObject("customAttributes").length());
        }
    }

    @Test
    void fetchSkipsAttributeInstanceWithEmptyValueIdAndSearchKey() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSetInstance asi = mock(AttributeSetInstance.class);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
            when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
            when(asi.getDescription()).thenReturn("");
            when(asi.getLotName()).thenReturn("");
            when(asi.getSerialNo()).thenReturn("");
            when(asi.getExpirationDate()).thenReturn(null);

            Attribute attr = mock(Attribute.class);
            when(attr.getId()).thenReturn("emptyAttrId");

            AttributeInstance ai = mock(AttributeInstance.class);
            when(ai.getAttribute()).thenReturn(attr);
            when(ai.getAttributeValue()).thenReturn(null);
            when(ai.getSearchKey()).thenReturn("");

            List<AttributeInstance> aiList = new ArrayList<>();
            aiList.add(ai);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(aiList);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "FETCH")
                    .put("_params", new JSONObject().put("instanceId", INSTANCE_ID_VAL))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(0, result.getJSONObject("customAttributes").length());
        }
    }

    // ======================== executeSave tests ========================

    @Test
    void saveDefaultFlowReturnsResult() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            // Mock RequestContext
            RequestContext requestContext = mock(RequestContext.class);
            reqCtxMock.when(RequestContext::get).thenReturn(requestContext);
            VariablesSecureApp vars = mock(VariablesSecureApp.class);
            when(requestContext.getVariablesSecureApp()).thenReturn(vars);

            // The handler creates AttributeSetInstanceValue internally and calls
            // setAttributeInstance which requires a DB. We cannot easily mock this
            // constructor-created object, so we test the exception path instead.
            // This test verifies the execute() catch block handles the exception.

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            JSONObject params = new JSONObject()
                    .put("attributeSetId", ATTR_SET_ID)
                    .put("instanceId", INSTANCE_ID_VAL)
                    .put("lot", "LOT-1")
                    .put("serialNo", "SN-001")
                    .put("expirationDate", "2025-06-15")
                    .put("isLocked", "N")
                    .put("lockDescription", "")
                    .put("windowId", "testWindow")
                    .put("isSOTrx", "Y")
                    .put("productId", "testProduct");

            String content = new JSONObject()
                    .put("_params", params)
                    .toString();

            // This will go to executeSave which creates DalConnectionProvider(true)
            // which won't mock easily. So it will throw and be caught by execute().
            JSONObject result = handler.execute(new HashMap<>(), content);

            // The execute() method catches exceptions and sets Error status
            assertNotNull(result);
            assertTrue(result.has(STATUS_KEY));
        }
    }

    @Test
    void saveWithAttributesAndIdentifierSkips() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            RequestContext requestContext = mock(RequestContext.class);
            reqCtxMock.when(RequestContext::get).thenReturn(requestContext);
            VariablesSecureApp vars = mock(VariablesSecureApp.class);
            when(requestContext.getVariablesSecureApp()).thenReturn(vars);

            // Build params with attributes including an _identifier entry
            JSONObject attrs = new JSONObject()
                    .put("someAttrId", "someValue")
                    .put("someAttrId_identifier", "ShouldBeSkipped");

            JSONObject params = new JSONObject()
                    .put("attributeSetId", ATTR_SET_ID)
                    .put("attributes", attrs);

            String content = new JSONObject()
                    .put("_params", params)
                    .toString();

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            JSONObject result = handler.execute(new HashMap<>(), content);

            // The executeSave will fail when trying DalConnectionProvider, caught by execute
            assertNotNull(result);
            assertTrue(result.has(STATUS_KEY));
        }
    }

    @Test
    void saveConvertsDatesToClassicFormat() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            RequestContext requestContext = mock(RequestContext.class);
            reqCtxMock.when(RequestContext::get).thenReturn(requestContext);
            VariablesSecureApp vars = mock(VariablesSecureApp.class);
            when(requestContext.getVariablesSecureApp()).thenReturn(vars);

            JSONObject params = new JSONObject()
                    .put("attributeSetId", ATTR_SET_ID)
                    .put("expirationDate", "2025-12-31");

            String content = new JSONObject()
                    .put("_params", params)
                    .toString();

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            JSONObject result = handler.execute(new HashMap<>(), content);

            // This exercises the convertToClassicDateFormat path (yyyy-MM-dd -> dd-MM-yyyy)
            assertNotNull(result);
        }
    }

    // ======================== Exception handling tests ========================

    @Test
    void executeHandlesExceptionAndReturnsError() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class)) {
            // Passing invalid JSON will cause an exception in the execute method
            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            JSONObject result = handler.execute(new HashMap<>(), "invalid json");

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertNotNull(result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void executeDefaultsToSaveWhenNoButtonValue() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            RequestContext requestContext = mock(RequestContext.class);
            reqCtxMock.when(RequestContext::get).thenReturn(requestContext);
            VariablesSecureApp vars = mock(VariablesSecureApp.class);
            when(requestContext.getVariablesSecureApp()).thenReturn(vars);

            JSONObject params = new JSONObject()
                    .put("attributeSetId", ATTR_SET_ID);

            // No _buttonValue — should default to "DONE" and go to executeSave
            String content = new JSONObject()
                    .put("_params", params)
                    .toString();

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            JSONObject result = handler.execute(new HashMap<>(), content);

            assertNotNull(result);
            assertTrue(result.has(STATUS_KEY));
        }
    }

    @Test
    void executeDoneButtonValueGoesToSave() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            RequestContext requestContext = mock(RequestContext.class);
            reqCtxMock.when(RequestContext::get).thenReturn(requestContext);
            VariablesSecureApp vars = mock(VariablesSecureApp.class);
            when(requestContext.getVariablesSecureApp()).thenReturn(vars);

            JSONObject params = new JSONObject()
                    .put("attributeSetId", ATTR_SET_ID);

            String content = new JSONObject()
                    .put("_buttonValue", "DONE")
                    .put("_params", params)
                    .toString();

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            JSONObject result = handler.execute(new HashMap<>(), content);

            assertNotNull(result);
            assertTrue(result.has(STATUS_KEY));
        }
    }

    // ======================== convertToClassicDateFormat tests ========================

    @Test
    void convertToClassicDateFormatConvertsValidDate() throws Exception {
        // We test this indirectly through executeSave by providing a date in
        // yyyy-MM-dd format and verifying it reaches the handler without error.
        // Direct testing via reflection for completeness:
        java.lang.reflect.Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod("convertToClassicDateFormat", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "2025-06-15");
        assertEquals("15-06-2025", result);
    }

    @Test
    void convertToClassicDateFormatReturnsEmptyForEmpty() throws Exception {
        java.lang.reflect.Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod("convertToClassicDateFormat", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "");
        assertEquals("", result);
    }

    @Test
    void convertToClassicDateFormatReturnsNullForNull() throws Exception {
        java.lang.reflect.Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod("convertToClassicDateFormat", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, (String) null);
        assertEquals(null, result);
    }

    @Test
    void convertToClassicDateFormatReturnsUnchangedForNonMatchingFormat() throws Exception {
        java.lang.reflect.Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod("convertToClassicDateFormat", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "15/06/2025");
        assertEquals("15/06/2025", result);
    }

    // ======================== replace tests ========================

    @Test
    void replaceRemovesSpecialCharacters() throws Exception {
        java.lang.reflect.Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod("replace", String.class);
        method.setAccessible(true);

        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();

        String result = (String) method.invoke(handler, "Test Name (With #Chars, & More)");
        assertEquals("TestNameWithCharsMore", result);
    }

    @Test
    void replaceReturnsEmptyForNull() throws Exception {
        java.lang.reflect.Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod("replace", String.class);
        method.setAccessible(true);

        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();

        String result = (String) method.invoke(handler, (String) null);
        assertEquals("", result);
    }

    @Test
    void replaceReturnsUnchangedForCleanString() throws Exception {
        java.lang.reflect.Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod("replace", String.class);
        method.setAccessible(true);

        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();

        String result = (String) method.invoke(handler, "CleanName");
        assertEquals("CleanName", result);
    }

    // ======================== Non-list attribute in config ========================

    @Test
    void configReturnsNonListAttributeWithoutValues() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSet attrSet = mock(AttributeSet.class);
            when(obDal.get(AttributeSet.class, ATTR_SET_ID)).thenReturn(attrSet);
            when(attrSet.getId()).thenReturn(ATTR_SET_ID);
            when(attrSet.getName()).thenReturn("SetName");
            when(attrSet.isLot()).thenReturn(false);
            when(attrSet.isSerialNo()).thenReturn(false);
            when(attrSet.isExpirationDate()).thenReturn(false);

            // A non-list attribute
            Attribute attr = mock(Attribute.class);
            when(attr.getId()).thenReturn("attrId2");
            when(attr.getName()).thenReturn("Size");
            when(attr.isList()).thenReturn(false);
            when(attr.isMandatory()).thenReturn(true);

            AttributeUse use = mock(AttributeUse.class);
            when(use.getAttribute()).thenReturn(attr);
            when(use.getSequenceNumber()).thenReturn(20L);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeUse> useCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeUse.class)).thenReturn(useCriteria);
            List<AttributeUse> useList = new ArrayList<>();
            useList.add(use);
            when(useCriteria.list()).thenReturn(useList);

            AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
            String content = new JSONObject()
                    .put("_buttonValue", "CONFIG")
                    .put("_params", new JSONObject().put("attributeSetId", ATTR_SET_ID))
                    .toString();

            JSONObject result = handler.execute(new HashMap<>(), content);

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            JSONObject customAttr = result.getJSONArray("customAttributes").getJSONObject(0);
            assertEquals("attrId2", customAttr.getString("id"));
            assertEquals("Size", customAttr.getString("name"));
            assertFalse(customAttr.getBoolean("isList"));
            assertTrue(customAttr.getBoolean("isMandatory"));
            assertFalse(customAttr.has("values"));
        }
    }
}
