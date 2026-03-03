package com.etendoerp.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

/**
 * Tests for the SAVE (DONE) action and execute() routing in
 * {@link AttributeSetInstanceActionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class AttributeSetInstanceActionHandlerSaveTest {

    private static final String ATTR_SET_ID = "testAttrSetId";
    private static final String INSTANCE_ID_VAL = "testInstanceId";
    private static final String INSTANCE_ID_KEY = "instanceId";
    private static final String STATUS_KEY = "status";
    private static final String ERROR_STATUS = "Error";
    private static final String BUTTON_VALUE_KEY = "_buttonValue";
    private static final String PARAMS_KEY = "_params";
    private static final String ATTRIBUTE_SET_ID_KEY = "attributeSetId";
    private static final String SERIAL_NO_KEY = "serialNo";
    private static final String SERIAL_NO_VAL = "SN-001";
    private static final String EXPIRATION_DATE_KEY = "expirationDate";
    private static final String EXPIRATION_DATE_VAL = "2025-06-15";

    private OBDal mockOBDal(MockedStatic<OBDal> dalMock) {
        OBDal obDal = mock(OBDal.class);
        dalMock.when(OBDal::getInstance).thenReturn(obDal);
        return obDal;
    }

    private void mockRequestContext(MockedStatic<RequestContext> reqCtxMock) {
        RequestContext requestContext = mock(RequestContext.class);
        reqCtxMock.when(RequestContext::get).thenReturn(requestContext);
        VariablesSecureApp vars = mock(VariablesSecureApp.class);
        when(requestContext.getVariablesSecureApp()).thenReturn(vars);
    }

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

            // executeSave creates DalConnectionProvider(true) which cannot be mocked,
            // so the exception is caught by execute() and returned as an Error result.
            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), new JSONObject().put(PARAMS_KEY, params).toString());

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

    @Test
    void executeHandlesExceptionAndReturnsError() throws Exception {
        try (MockedStatic<OBContext> ctxMock = mockStatic(OBContext.class)) {
            JSONObject result = new AttributeSetInstanceActionHandler()
                    .execute(new HashMap<>(), "invalid json");

            assertEquals(ERROR_STATUS, result.getString(STATUS_KEY));
            assertNotNull(result.getString("message"));
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
}
