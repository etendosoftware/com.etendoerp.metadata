package com.etendoerp.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.plm.Attribute;
import org.openbravo.model.common.plm.AttributeInstance;
import org.openbravo.model.common.plm.AttributeSetInstance;
import org.openbravo.model.common.plm.AttributeValue;

/**
 * Tests for the FETCH action of {@link AttributeSetInstanceActionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class AttributeSetInstanceActionHandlerFetchTest {

    private static final String INSTANCE_ID_VAL = "testInstanceId";
    private static final String STATUS_KEY = "status";
    private static final String MESSAGE_KEY = "message";
    private static final String SUCCESS_STATUS = "Success";
    private static final String ERROR_STATUS = "Error";
    private static final String BUTTON_VALUE_FETCH = "FETCH";
    private static final String BUTTON_VALUE_KEY = "_buttonValue";
    private static final String PARAMS_KEY = "_params";
    private static final String INSTANCE_ID_KEY = "instanceId";
    private static final String CUSTOM_ATTRIBUTES_KEY = "customAttributes";
    private static final String SERIAL_NO_KEY = "serialNo";
    private static final String SERIAL_NO_VAL = "SN-001";
    private static final String EXPIRATION_DATE_KEY = "expirationDate";
    private static final String EXPIRATION_DATE_VAL = "2025-06-15";
    private static final String FREE_TEXT_VALUE = "FreeTextValue";

    private OBDal mockOBDal(MockedStatic<OBDal> dalMock) {
        OBDal obDal = mock(OBDal.class);
        dalMock.when(OBDal::getInstance).thenReturn(obDal);
        return obDal;
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

    private String buildFetchContent(String instanceId) throws Exception {
        return new JSONObject()
                .put(BUTTON_VALUE_KEY, BUTTON_VALUE_FETCH)
                .put(PARAMS_KEY, new JSONObject().put(INSTANCE_ID_KEY, instanceId))
                .toString();
    }

    @Test
    void fetchReturnsErrorWhenInstanceIdIsEmpty() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class)) {
            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildFetchContent(""));

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("No instance ID provided", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void fetchReturnsErrorWhenInstanceIdIsZero() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class)) {
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
}
