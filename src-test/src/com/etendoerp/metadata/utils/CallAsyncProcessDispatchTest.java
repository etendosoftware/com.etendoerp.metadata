/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance
 * with the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright (C) 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */

package com.etendoerp.metadata.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.scheduling.ProcessBundle;

/**
 * Tests for {@link CallAsyncProcess} dispatch resolution (resolveJavaClassName,
 * hasJavaClass, hasProcedure), bundle-param population, result application and
 * parameter stringification. Split out of {@code CallAsyncProcessExtendedTest}
 * to keep each test class under the method-count limit.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CallAsyncProcessDispatchTest {

    private static final String RESOLVE_JAVA_CLASS_NAME_METHOD = "resolveJavaClassName";
    private static final String HAS_PROCEDURE_METHOD = "hasProcedure";
    private static final String APPLY_RESULT_TO_INSTANCE_METHOD = "applyResultToInstance";
    private static final String JAVA_CLASS = "org.openbravo.service.db.ExportClientProcess";
    private static final String TEST_PROCEDURE = "test_procedure";
    private static final String CLIENT_COLUMN = "AD_Client_ID";
    private static final String CLIENT_KEY = "adClientId";
    private static final String AUDIT_COLUMN = "ExportAuditInfo";
    private static final String AUDIT_KEY = "exportauditinfo";
    private static final String CLIENT_VALUE = "client-1";

    /** Invokes a private instance method of CallAsyncProcess by reflection. */
    private Object invokePrivate(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = CallAsyncProcess.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method.invoke(CallAsyncProcess.getInstance(), args);
    }

    private Process mockProcessWithModelObjects(String processClassName, ModelImplementation... modelObjects) {
        Process process = mock(Process.class);
        when(process.getJavaClassName()).thenReturn(processClassName);
        when(process.getADModelImplementationList()).thenReturn(Arrays.asList(modelObjects));
        return process;
    }

    private ModelImplementation mockModelObject(String action, String className) {
        ModelImplementation modelObject = mock(ModelImplementation.class);
        lenient().when(modelObject.getAction()).thenReturn(action);
        lenient().when(modelObject.getJavaClassName()).thenReturn(className);
        return modelObject;
    }

    /** Invokes applyResultToInstance with the given result under a mocked OBDal singleton. */
    private void invokeApplyResultToInstance(ProcessInstance pInstance, Object result) throws Exception {
        OBDal mockOBDal = mock(OBDal.class);
        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            invokePrivate(APPLY_RESULT_TO_INSTANCE_METHOD,
                    new Class<?>[] { ProcessInstance.class, Object.class }, pInstance, result);
        }
    }

    /**
     * Builds an OBError of the given type/message, runs applyResultToInstance and
     * asserts the result code and message it sets on the instance.
     */
    private void assertObErrorMapsTo(String type, String message, long expectedResult) throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        OBError result = new OBError();
        result.setType(type);
        result.setMessage(message);

        invokeApplyResultToInstance(pInstance, result);

        verify(pInstance).setResult(expectedResult);
        verify(pInstance).setErrorMsg(message);
    }

    // ========== dispatch (resolveJavaClassName / hasJavaClass / hasProcedure) Tests ==========

    /**
     * Verifies resolveJavaClassName prefers the process' own class name.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testResolveJavaClassNameFromProcess() throws Exception {
        Process process = mockProcessWithModelObjects(JAVA_CLASS);
        assertEquals(JAVA_CLASS,
                invokePrivate(RESOLVE_JAVA_CLASS_NAME_METHOD, new Class<?>[] { Process.class }, process));
    }

    /**
     * Verifies resolveJavaClassName falls back to the process-action ('P') model object class.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testResolveJavaClassNameFromModelObject() throws Exception {
        Process process = mockProcessWithModelObjects(null, mockModelObject("P", JAVA_CLASS));
        assertEquals(JAVA_CLASS,
                invokePrivate(RESOLVE_JAVA_CLASS_NAME_METHOD, new Class<?>[] { Process.class }, process));
    }

    /**
     * Verifies resolveJavaClassName ignores model objects whose action is not 'P'.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testResolveJavaClassNameIgnoresNonProcessAction() throws Exception {
        Process process = mockProcessWithModelObjects(null, mockModelObject("S", JAVA_CLASS));
        assertNull(invokePrivate(RESOLVE_JAVA_CLASS_NAME_METHOD, new Class<?>[] { Process.class }, process));
    }

    /**
     * Verifies resolveJavaClassName returns null when there is no class anywhere.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testResolveJavaClassNameNone() throws Exception {
        Process process = mockProcessWithModelObjects(null);
        assertNull(invokePrivate(RESOLVE_JAVA_CLASS_NAME_METHOD, new Class<?>[] { Process.class }, process));
    }

    /**
     * Verifies hasJavaClass reflects whether a Java class was resolved.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testHasJavaClass() throws Exception {
        Class<?>[] types = { Process.class };
        assertTrue((Boolean) invokePrivate("hasJavaClass", types, mockProcessWithModelObjects(JAVA_CLASS)));
        assertFalse((Boolean) invokePrivate("hasJavaClass", types, mockProcessWithModelObjects(null)));
    }

    /**
     * Verifies hasProcedure is true only for a non-empty stored procedure name.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testHasProcedure() throws Exception {
        Class<?>[] types = { Process.class };

        Process withProc = mock(Process.class);
        when(withProc.getProcedure()).thenReturn(TEST_PROCEDURE);
        Process emptyProc = mock(Process.class);
        when(emptyProc.getProcedure()).thenReturn("");
        Process nullProc = mock(Process.class);
        when(nullProc.getProcedure()).thenReturn(null);

        assertTrue((Boolean) invokePrivate(HAS_PROCEDURE_METHOD, types, withProc));
        assertFalse((Boolean) invokePrivate(HAS_PROCEDURE_METHOD, types, emptyProc));
        assertFalse((Boolean) invokePrivate(HAS_PROCEDURE_METHOD, types, nullProc));
    }

    // ========== populateBundleParams Tests ==========

    /**
     * Verifies populateBundleParams keys the bundle params with the Classic
     * column-name transformation (AD_Client_ID -> adClientId, ExportAuditInfo -> exportauditinfo).
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testPopulateBundleParamsUsesClassicKeys() throws Exception {
        ProcessBundle bundle = mock(ProcessBundle.class);
        Map<String, Object> bundleParams = new HashMap<>();
        when(bundle.getParams()).thenReturn(bundleParams);

        Map<String, String> parameters = new HashMap<>();
        parameters.put(CLIENT_COLUMN, CLIENT_VALUE);
        parameters.put(AUDIT_COLUMN, "Y");

        invokePrivate("populateBundleParams", new Class<?>[] { ProcessBundle.class, Map.class },
                bundle, parameters);

        assertEquals(CLIENT_VALUE, bundleParams.get(CLIENT_KEY));
        assertEquals("Y", bundleParams.get(AUDIT_KEY));
    }

    /**
     * Verifies populateBundleParams is a no-op when parameters is null.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testPopulateBundleParamsWithNullParameters() throws Exception {
        ProcessBundle bundle = mock(ProcessBundle.class);
        Map<String, Object> bundleParams = new HashMap<>();
        when(bundle.getParams()).thenReturn(bundleParams);

        invokePrivate("populateBundleParams", new Class<?>[] { ProcessBundle.class, Map.class },
                bundle, null);

        assertTrue("No params should be added", bundleParams.isEmpty());
    }

    // ========== applyResultToInstance / failNoImplementation Tests ==========

    /**
     * Verifies a Success OBError sets result 1 and the message on the instance.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testApplyResultToInstanceSuccess() throws Exception {
        assertObErrorMapsTo("Success", "Exported", 1L);
    }

    /**
     * Verifies an Error OBError sets result 0 and the message on the instance.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testApplyResultToInstanceError() throws Exception {
        assertObErrorMapsTo("Error", "Boom", 0L);
    }

    /**
     * Verifies a non-OBError result is treated as success.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testApplyResultToInstanceNonError() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);

        invokeApplyResultToInstance(pInstance, null);

        verify(pInstance).setResult(1L);
        verify(pInstance).setErrorMsg(null);
    }

    /**
     * Verifies failNoImplementation marks the instance as failed with a readable message.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testFailNoImplementation() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        when(process.getName()).thenReturn("Export Client");

        OBDal mockOBDal = mock(OBDal.class);
        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            invokePrivate("failNoImplementation",
                    new Class<?>[] { ProcessInstance.class, Process.class }, pInstance, process);
        }

        verify(pInstance).setResult(0L);
        verify(pInstance).setErrorMsg(
                "Process 'Export Client' has no stored procedure or Java class to execute.");
        verify(mockOBDal).save(pInstance);
    }

    // ========== toStringParameters Tests ==========

    /**
     * Verifies toStringParameters stringifies values and skips null entries.
     *
     * @throws Exception if reflection fails
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testToStringParameters() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put(CLIENT_COLUMN, CLIENT_VALUE);
        input.put("Amount", 5);
        input.put("Ignored", null);

        Map<String, String> result = (Map<String, String>) invokePrivate("toStringParameters",
                new Class<?>[] { Map.class }, input);

        assertEquals(CLIENT_VALUE, result.get(CLIENT_COLUMN));
        assertEquals("5", result.get("Amount"));
        assertFalse("null values must be skipped", result.containsKey("Ignored"));
    }

    /**
     * Verifies toStringParameters returns an empty map when given null.
     *
     * @throws Exception if reflection fails
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testToStringParametersWithNull() throws Exception {
        Map<String, String> result = (Map<String, String>) invokePrivate("toStringParameters",
                new Class<?>[] { Map.class }, (Object) null);

        assertTrue("Result should be empty for null input", result.isEmpty());
    }
}
