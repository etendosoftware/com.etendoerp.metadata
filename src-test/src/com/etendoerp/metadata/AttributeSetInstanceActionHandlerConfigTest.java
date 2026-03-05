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
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;
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
import org.openbravo.model.common.plm.AttributeSet;
import org.openbravo.model.common.plm.AttributeUse;
import org.openbravo.model.common.plm.AttributeValue;

/**
 * Tests for the CONFIG action of {@link AttributeSetInstanceActionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class AttributeSetInstanceActionHandlerConfigTest {

    private static final String ATTR_SET_ID = "testAttrSetId";
    private static final String STATUS_KEY = "status";
    private static final String MESSAGE_KEY = "message";
    private static final String SUCCESS_STATUS = "Success";
    private static final String ERROR_STATUS = "Error";
    private static final String BUTTON_VALUE_CONFIG = "CONFIG";
    private static final String BUTTON_VALUE_KEY = "_buttonValue";
    private static final String PARAMS_KEY = "_params";
    private static final String ATTRIBUTE_SET_ID_KEY = "attributeSetId";
    private static final String CUSTOM_ATTRIBUTES_KEY = "customAttributes";
    private static final String VALUES_KEY = "values";
    private static final String SET_NAME_VAL = "SetName";

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

    private String buildConfigContent() throws Exception {
        return new JSONObject()
                .put(BUTTON_VALUE_KEY, BUTTON_VALUE_CONFIG)
                .put(PARAMS_KEY, new JSONObject().put(ATTRIBUTE_SET_ID_KEY, ATTR_SET_ID))
                .toString();
    }

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
