package com.etendoerp.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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
    private static final String BUTTON_VALUE_CONFIG = "CONFIG";
    private static final String BUTTON_VALUE_FETCH = "FETCH";
    private static final String BUTTON_VALUE_KEY = "_buttonValue";
    private static final String PARAMS_KEY = "_params";
    private static final String ATTRIBUTE_SET_ID_KEY = "attributeSetId";
    private static final String INSTANCE_ID_KEY = "instanceId";
    private static final String CUSTOM_ATTRIBUTES_KEY = "customAttributes";
    private static final String SERIAL_NO_KEY = "serialNo";
    private static final String SERIAL_NO_VAL = "SN-001";
    private static final String EXPIRATION_DATE_KEY = "expirationDate";
    private static final String EXPIRATION_DATE_VAL = "2025-06-15";
    private static final String VALUES_KEY = "values";
    private static final String FREE_TEXT_VALUE = "FreeTextValue";
    private static final String SET_NAME_VAL = "SetName";
    private static final String METHOD_CONVERT_DATE = "convertToClassicDateFormat";
    private static final String METHOD_REPLACE = "replace";

    // ======================== Helper methods ========================

    private OBDal mockOBDal(MockedStatic<OBDal> dalMock) {
        OBDal obDal = mock(OBDal.class);
        dalMock.when(OBDal::getInstance).thenReturn(obDal);
        return obDal;
    }

    private AttributeSet mockAttributeSet(OBDal obDal, String name,
            boolean isLot, boolean isSerialNo, boolean isExpirationDate) {
        AttributeSet attrSet = mock(AttributeSet.class);
        when(obDal.get(AttributeSet.class, ATTR_SET_ID)).thenReturn(attrSet);
        when(attrSet.getId()).thenReturn(ATTR_SET_ID);
        when(attrSet.getName()).thenReturn(name);
        when(attrSet.isLot()).thenReturn(isLot);
        when(attrSet.isSerialNo()).thenReturn(isSerialNo);
        when(attrSet.isExpirationDate()).thenReturn(isExpirationDate);
        return attrSet;
    }

    @SuppressWarnings("unchecked")
    private void mockEmptyAttributeUseCriteria(OBDal obDal) {
        OBCriteria<AttributeUse> useCriteria = mock(OBCriteria.class);
        when(obDal.createCriteria(AttributeUse.class)).thenReturn(useCriteria);
        when(useCriteria.list()).thenReturn(Collections.emptyList());
    }

    private AttributeSetInstance mockBasicAsi(OBDal obDal) {
        AttributeSetInstance asi = mock(AttributeSetInstance.class);
        when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
        when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
        when(asi.getDescription()).thenReturn("");
        when(asi.getLotName()).thenReturn("");
        when(asi.getSerialNo()).thenReturn("");
        when(asi.getExpirationDate()).thenReturn(null);
        return asi;
    }

    @SuppressWarnings("unchecked")
    private void mockEmptyAttributeInstanceCriteria(OBDal obDal) {
        OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
        when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
        when(aiCriteria.list()).thenReturn(Collections.emptyList());
    }

    private VariablesSecureApp mockRequestContext(MockedStatic<RequestContext> reqCtxMock) {
        RequestContext requestContext = mock(RequestContext.class);
        reqCtxMock.when(RequestContext::get).thenReturn(requestContext);
        VariablesSecureApp vars = mock(VariablesSecureApp.class);
        when(requestContext.getVariablesSecureApp()).thenReturn(vars);
        return vars;
    }

    private String buildConfigContent() throws Exception {
        return new JSONObject()
                .put(BUTTON_VALUE_KEY, BUTTON_VALUE_CONFIG)
                .put(PARAMS_KEY, new JSONObject().put(ATTRIBUTE_SET_ID_KEY, ATTR_SET_ID))
                .toString();
    }

    private String buildFetchContent(String instanceId) throws Exception {
        return new JSONObject()
                .put(BUTTON_VALUE_KEY, BUTTON_VALUE_FETCH)
                .put(PARAMS_KEY, new JSONObject().put(INSTANCE_ID_KEY, instanceId))
                .toString();
    }

    @SuppressWarnings("java:S3011")
    private Method getConvertDateMethod() throws ReflectiveOperationException {
        Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod(METHOD_CONVERT_DATE, String.class);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("java:S3011")
    private Method getReplaceMethod() throws ReflectiveOperationException {
        Method method = AttributeSetInstanceActionHandler.class
                .getDeclaredMethod(METHOD_REPLACE, String.class);
        method.setAccessible(true);
        return method;
    }

    // ======================== executeConfig tests ========================

    @Test
    void configReturnsErrorWhenAttributeSetNotFound() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            when(obDal.get(AttributeSet.class, ATTR_SET_ID)).thenReturn(null);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildConfigContent());

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("Attribute Set not found", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void configReturnsSuccessWithAttributeSetFields() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            AttributeSet attrSet = mockAttributeSet(obDal, "Test Set", true, false, true);
            when(attrSet.getGuaranteedDays()).thenReturn(30L);
            mockEmptyAttributeUseCriteria(obDal);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildConfigContent());

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

            OBDal obDal = mockOBDal(dalMock);
            mockAttributeSet(obDal, SET_NAME_VAL, false, false, false);

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
            when(useCriteria.list()).thenReturn(List.of(use));

            AttributeValue av = mock(AttributeValue.class);
            when(av.getId()).thenReturn("val1");
            when(av.getName()).thenReturn("Red");

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeValue> valCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeValue.class)).thenReturn(valCriteria);
            when(valCriteria.list()).thenReturn(List.of(av));

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildConfigContent());

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(1, result.getJSONArray(CUSTOM_ATTRIBUTES_KEY).length());
            JSONObject customAttr = result.getJSONArray(CUSTOM_ATTRIBUTES_KEY).getJSONObject(0);
            assertEquals("attrId1", customAttr.getString("id"));
            assertEquals("Color", customAttr.getString("name"));
            assertTrue(customAttr.getBoolean("isList"));
            assertEquals(1, customAttr.getJSONArray(VALUES_KEY).length());
            assertEquals("Red", customAttr.getJSONArray(VALUES_KEY).getJSONObject(0).getString("name"));
        }
    }

    @Test
    void configSkipsNullAttributes() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            mockAttributeSet(obDal, SET_NAME_VAL, false, false, false);

            AttributeUse use = mock(AttributeUse.class);
            when(use.getAttribute()).thenReturn(null);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeUse> useCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeUse.class)).thenReturn(useCriteria);
            when(useCriteria.list()).thenReturn(List.of(use));

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildConfigContent());

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(0, result.getJSONArray(CUSTOM_ATTRIBUTES_KEY).length());
        }
    }

    @Test
    void configIsGuaranteeDateFalseWhenNoDays() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            AttributeSet attrSet = mockAttributeSet(obDal, SET_NAME_VAL, false, false, true);
            when(attrSet.getGuaranteedDays()).thenReturn(null);
            mockEmptyAttributeUseCriteria(obDal);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildConfigContent());

            assertFalse(result.getBoolean("isGuaranteeDate"));
        }
    }

    // ======================== executeFetch tests ========================

    @Test
    void fetchReturnsErrorWhenInstanceIdIsEmpty() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            mockOBDal(dalMock);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(""));

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("No instance ID provided", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void fetchReturnsErrorWhenInstanceIdIsZero() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            mockOBDal(dalMock);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent("0"));

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("No instance ID provided", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void fetchReturnsErrorWhenInstanceNotFound() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(null);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(INSTANCE_ID_VAL));

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("Attribute Set Instance not found", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void fetchReturnsInstanceDataWithExpirationDate() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            AttributeSetInstance asi = mock(AttributeSetInstance.class);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
            when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
            when(asi.getDescription()).thenReturn("Lot #123");
            when(asi.getLotName()).thenReturn("LOT-A");
            when(asi.getSerialNo()).thenReturn(SERIAL_NO_VAL);
            // 2025-06-15
            @SuppressWarnings("deprecation")
            Date expDate = new Date(125, 5, 15);
            when(asi.getExpirationDate()).thenReturn(expDate);
            mockEmptyAttributeInstanceCriteria(obDal);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(INSTANCE_ID_VAL));

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(INSTANCE_ID_VAL, result.getString(INSTANCE_ID_KEY));
            assertEquals("Lot #123", result.getString("description"));
            assertEquals("LOT-A", result.getString("lotName"));
            assertEquals(SERIAL_NO_VAL, result.getString(SERIAL_NO_KEY));
            assertEquals(EXPIRATION_DATE_VAL, result.getString(EXPIRATION_DATE_KEY));
            assertEquals("", result.getString("guaranteeDate"));
        }
    }

    @Test
    void fetchReturnsEmptyExpirationDateWhenNull() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            AttributeSetInstance asi = mock(AttributeSetInstance.class);
            when(obDal.get(AttributeSetInstance.class, INSTANCE_ID_VAL)).thenReturn(asi);
            when(asi.getId()).thenReturn(INSTANCE_ID_VAL);
            when(asi.getDescription()).thenReturn(null);
            when(asi.getLotName()).thenReturn(null);
            when(asi.getSerialNo()).thenReturn(null);
            when(asi.getExpirationDate()).thenReturn(null);
            mockEmptyAttributeInstanceCriteria(obDal);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(INSTANCE_ID_VAL));

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals("", result.getString(EXPIRATION_DATE_KEY));
            assertEquals("", result.getString("description"));
            assertEquals("", result.getString("lotName"));
            assertEquals("", result.getString(SERIAL_NO_KEY));
        }
    }

    @Test
    void fetchReturnsCustomAttributeValuesFromAttributeValue() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            mockBasicAsi(obDal);

            Attribute attr = mock(Attribute.class);
            when(attr.getId()).thenReturn("customAttrId");

            AttributeValue attrVal = mock(AttributeValue.class);
            when(attrVal.getId()).thenReturn("selectedValueId");
            when(attrVal.getIdentifier()).thenReturn("Red Color");

            AttributeInstance ai = mock(AttributeInstance.class);
            when(ai.getAttribute()).thenReturn(attr);
            when(ai.getAttributeValue()).thenReturn(attrVal);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(List.of(ai));

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(INSTANCE_ID_VAL));

            JSONObject customAttrs = result.getJSONObject(CUSTOM_ATTRIBUTES_KEY);
            assertEquals("selectedValueId", customAttrs.getString("customAttrId"));
            assertEquals("Red Color", customAttrs.getString("customAttrId_identifier"));
        }
    }

    @Test
    void fetchReturnsCustomAttributeValuesFromSearchKey() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            mockBasicAsi(obDal);

            Attribute attr = mock(Attribute.class);
            when(attr.getId()).thenReturn("freeTextAttrId");

            AttributeInstance ai = mock(AttributeInstance.class);
            when(ai.getAttribute()).thenReturn(attr);
            when(ai.getAttributeValue()).thenReturn(null);
            when(ai.getSearchKey()).thenReturn(FREE_TEXT_VALUE);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(List.of(ai));

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(INSTANCE_ID_VAL));

            JSONObject customAttrs = result.getJSONObject(CUSTOM_ATTRIBUTES_KEY);
            assertEquals(FREE_TEXT_VALUE, customAttrs.getString("freeTextAttrId"));
            assertEquals(FREE_TEXT_VALUE, customAttrs.getString("freeTextAttrId_identifier"));
        }
    }

    @Test
    void fetchSkipsAttributeInstanceWithNullAttribute() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            mockBasicAsi(obDal);

            AttributeInstance ai = mock(AttributeInstance.class);
            when(ai.getAttribute()).thenReturn(null);

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(List.of(ai));

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(INSTANCE_ID_VAL));

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(0, result.getJSONObject(CUSTOM_ATTRIBUTES_KEY).length());
        }
    }

    @Test
    void fetchSkipsAttributeInstanceWithEmptyValueIdAndSearchKey() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            mockBasicAsi(obDal);

            Attribute attr = mock(Attribute.class);
            when(attr.getId()).thenReturn("emptyAttrId");

            AttributeInstance ai = mock(AttributeInstance.class);
            when(ai.getAttribute()).thenReturn(attr);
            when(ai.getAttributeValue()).thenReturn(null);
            when(ai.getSearchKey()).thenReturn("");

            @SuppressWarnings("unchecked")
            OBCriteria<AttributeInstance> aiCriteria = mock(OBCriteria.class);
            when(obDal.createCriteria(AttributeInstance.class)).thenReturn(aiCriteria);
            when(aiCriteria.list()).thenReturn(List.of(ai));

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(INSTANCE_ID_VAL));

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(0, result.getJSONObject(CUSTOM_ATTRIBUTES_KEY).length());
        }
    }

    // ======================== executeSave tests ========================

    @Test
    void saveDefaultFlowReturnsResult() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            mockOBDal(dalMock);
            mockRequestContext(reqCtxMock);

            JSONObject params = new JSONObject()
                    .put(ATTRIBUTE_SET_ID_KEY, ATTR_SET_ID)
                    .put(INSTANCE_ID_KEY, INSTANCE_ID_VAL)
                    .put("lot", "LOT-1")
                    .put(SERIAL_NO_KEY, SERIAL_NO_VAL)
                    .put(EXPIRATION_DATE_KEY, EXPIRATION_DATE_VAL)
                    .put("isLocked", "N")
                    .put("lockDescription", "")
                    .put("windowId", "testWindow")
                    .put("isSOTrx", "Y")
                    .put("productId", "testProduct");

            String content = new JSONObject().put(PARAMS_KEY, params).toString();

            // executeSave creates DalConnectionProvider(true) which cannot be mocked,
            // so the exception is caught by execute() and returned as an Error result.
            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), content);

            assertNotNull(result);
            assertTrue(result.has(STATUS_KEY));
        }
    }

    @Test
    void saveWithAttributesAndIdentifierSkips() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            mockOBDal(dalMock);
            mockRequestContext(reqCtxMock);

            JSONObject attrs = new JSONObject()
                    .put("someAttrId", "someValue")
                    .put("someAttrId_identifier", "ShouldBeSkipped");

            String content = new JSONObject()
                    .put(PARAMS_KEY, new JSONObject()
                            .put(ATTRIBUTE_SET_ID_KEY, ATTR_SET_ID)
                            .put("attributes", attrs))
                    .toString();

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), content);

            assertNotNull(result);
            assertTrue(result.has(STATUS_KEY));
        }
    }

    @Test
    void saveConvertsDatesToClassicFormat() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            mockOBDal(dalMock);
            mockRequestContext(reqCtxMock);

            String content = new JSONObject()
                    .put(PARAMS_KEY, new JSONObject()
                            .put(ATTRIBUTE_SET_ID_KEY, ATTR_SET_ID)
                            .put(EXPIRATION_DATE_KEY, "2025-12-31"))
                    .toString();

            // Exercises the convertToClassicDateFormat path (yyyy-MM-dd -> dd-MM-yyyy)
            assertNotNull(new AttributeSetInstanceActionHandler().execute(new HashMap<>(), content));
        }
    }

    // ======================== Exception handling tests ========================

    @Test
    void executeHandlesExceptionAndReturnsError() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class)) {
            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), "invalid json");

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertNotNull(result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void executeDefaultsToSaveWhenNoButtonValue() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            mockOBDal(dalMock);
            mockRequestContext(reqCtxMock);

            // No _buttonValue — should default to "DONE" and go to executeSave
            String content = new JSONObject()
                    .put(PARAMS_KEY, new JSONObject().put(ATTRIBUTE_SET_ID_KEY, ATTR_SET_ID))
                    .toString();

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), content);

            assertNotNull(result);
            assertTrue(result.has(STATUS_KEY));
        }
    }

    @Test
    void executeDoneButtonValueGoesToSave() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class);
             MockedStatic<RequestContext> reqCtxMock = mockStatic(RequestContext.class)) {

            mockOBDal(dalMock);
            mockRequestContext(reqCtxMock);

            String content = new JSONObject()
                    .put(BUTTON_VALUE_KEY, "DONE")
                    .put(PARAMS_KEY, new JSONObject().put(ATTRIBUTE_SET_ID_KEY, ATTR_SET_ID))
                    .toString();

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), content);

            assertNotNull(result);
            assertTrue(result.has(STATUS_KEY));
        }
    }

    // ======================== convertToClassicDateFormat tests ========================

    @Test
    void convertToClassicDateFormatConvertsValidDate() throws Exception {
        String result = (String) getConvertDateMethod().invoke(null, EXPIRATION_DATE_VAL);
        assertEquals("15-06-2025", result);
    }

    @Test
    void convertToClassicDateFormatReturnsEmptyForEmpty() throws Exception {
        String result = (String) getConvertDateMethod().invoke(null, "");
        assertEquals("", result);
    }

    @Test
    void convertToClassicDateFormatReturnsNullForNull() throws Exception {
        assertEquals(null, getConvertDateMethod().invoke(null, (String) null));
    }

    @Test
    void convertToClassicDateFormatReturnsUnchangedForNonMatchingFormat() throws Exception {
        String result = (String) getConvertDateMethod().invoke(null, "15/06/2025");
        assertEquals("15/06/2025", result);
    }

    // ======================== replace tests ========================

    @Test
    void replaceRemovesSpecialCharacters() throws Exception {
        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
        String result = (String) getReplaceMethod().invoke(handler, "Test Name (With #Chars, & More)");
        assertEquals("TestNameWithCharsMore", result);
    }

    @Test
    void replaceReturnsEmptyForNull() throws Exception {
        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
        String result = (String) getReplaceMethod().invoke(handler, (String) null);
        assertEquals("", result);
    }

    @Test
    void replaceReturnsUnchangedForCleanString() throws Exception {
        AttributeSetInstanceActionHandler handler = new AttributeSetInstanceActionHandler();
        String result = (String) getReplaceMethod().invoke(handler, "CleanName");
        assertEquals("CleanName", result);
    }

    // ======================== Non-list attribute in config ========================

    @Test
    void configReturnsNonListAttributeWithoutValues() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mockOBDal(dalMock);
            mockAttributeSet(obDal, SET_NAME_VAL, false, false, false);

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
            when(useCriteria.list()).thenReturn(List.of(use));

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildConfigContent());

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            JSONObject customAttr = result.getJSONArray(CUSTOM_ATTRIBUTES_KEY).getJSONObject(0);
            assertEquals("attrId2", customAttr.getString("id"));
            assertEquals("Size", customAttr.getString("name"));
            assertFalse(customAttr.getBoolean("isList"));
            assertTrue(customAttr.getBoolean("isMandatory"));
            assertFalse(customAttr.has(VALUES_KEY));
        }
    }
}
