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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.plm.AttributeSet;
import org.openbravo.model.common.plm.Product;

/**
 * Tests for the PRODUCT action of {@link AttributeSetInstanceActionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class AttributeSetInstanceActionHandlerProductConfigTest {

    private static final String PRODUCT_ID = "testProductId";
    private static final String ATTR_SET_ID = "testAttrSetId";
    private static final String STATUS_KEY = "status";
    private static final String MESSAGE_KEY = "message";
    private static final String SUCCESS_STATUS = "Success";
    private static final String ERROR_STATUS = "Error";
    private static final String BUTTON_VALUE_PRODUCT = "PRODUCT";
    private static final String BUTTON_VALUE_KEY = "_buttonValue";
    private static final String PARAMS_KEY = "_params";
    private static final String PRODUCT_ID_KEY = "productId";
    private static final String ATTRIBUTE_SET_ID_KEY = "attributeSetId";

    private String buildProductContent() throws Exception {
        return new JSONObject()
                .put(BUTTON_VALUE_KEY, BUTTON_VALUE_PRODUCT)
                .put(PARAMS_KEY, new JSONObject().put(PRODUCT_ID_KEY, PRODUCT_ID))
                .toString();
    }

    @Test
    void productConfigReturnsErrorWhenProductNotFound() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.get(Product.class, PRODUCT_ID)).thenReturn(null);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildProductContent());

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("Product not found", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void productConfigReturnsErrorWhenNoAttributeSet() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            Product product = mock(Product.class);
            when(obDal.get(Product.class, PRODUCT_ID)).thenReturn(product);
            when(product.getAttributeSet()).thenReturn(null);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildProductContent());

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertEquals("No Attribute Set configured for this product", result.getString(MESSAGE_KEY));
        }
    }

    @Test
    void productConfigReturnsSuccessWithAttributeSetId() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class);
             MockedStatic<OBDal> dalMock = mockStatic(OBDal.class)) {

            OBDal obDal = mock(OBDal.class);
            dalMock.when(OBDal::getInstance).thenReturn(obDal);

            AttributeSet attributeSet = mock(AttributeSet.class);
            when(attributeSet.getId()).thenReturn(ATTR_SET_ID);

            Product product = mock(Product.class);
            when(obDal.get(Product.class, PRODUCT_ID)).thenReturn(product);
            when(product.getAttributeSet()).thenReturn(attributeSet);

            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), buildProductContent());

            assertEquals(SUCCESS_STATUS, result.getString(STATUS_KEY));
            assertEquals(ATTR_SET_ID, result.getString(ATTRIBUTE_SET_ID_KEY));
        }
    }
}
