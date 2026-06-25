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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.hibernate.Session;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.domain.ModelImplementation;
import org.openbravo.model.ad.process.Parameter;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;
import org.openbravo.scheduling.ProcessBundle;

/**
 * Extended tests for {@link CallAsyncProcess} covering ContextValues,
 * hydrateContext, executeProcedure branches, and runInBackground error paths.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CallAsyncProcessExtendedTest {

    private static final String CONTEXT_VALUES_CLASS = "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues";
    private static final String HYDRATE_CONTEXT_METHOD = "hydrateContext";
    private static final String BBDD_RDBMS_PROPERTY = "bbdd.rdbms";
    private static final String TEST_PROCEDURE = "test_procedure";
    private static final String EXECUTE_PROCEDURE_METHOD = "executeProcedure";
    private static final String USER_ID = "USER_ID";
    private static final String ROLE_ID = "ROLE_ID";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String ORG_ID = "ORG_ID";
    private static final String LANG_ID = "en_US";
    private static final String WAREHOUSE_ID = "WAREHOUSE_ID";
    private static final String PINSTANCE_ID = "PI_ID";
    private static final String PROCESS_ID = "PROC_ID";
    private static final String CLIENT_COLUMN = "AD_Client_ID";
    private static final String CLIENT_KEY = "adClientId";
    private static final String AUDIT_COLUMN = "ExportAuditInfo";
    private static final String AUDIT_KEY = "exportauditinfo";
    private static final String CLIENT_VALUE = "client-1";

    // ========== ContextValues Tests ==========

    /**
     * Verifies ContextValues fields are null when constructed with a null OBContext.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testContextValuesWithNullContext() throws Exception {
        Class<?> cvClass = Class.forName(
                CONTEXT_VALUES_CLASS);
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);

        Object cv = ctor.newInstance((OBContext) null);

        Field userIdField = cvClass.getDeclaredField("userId");
        userIdField.setAccessible(true);
        assertNull("userId should be null when context is null", userIdField.get(cv));
    }

    /**
     * Verifies warehouseId is null when the OBContext has no warehouse set.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testContextValuesWithNullWarehouse() throws Exception {
        Object cv = newContextValues(createMockContext(null));

        assertNull("warehouseId should be null when warehouse is null",
                getContextValueField(cv, "warehouseId"));
        assertEquals(USER_ID, getContextValueField(cv, "userId"));
    }

    /**
     * Verifies warehouseId is populated when the OBContext includes a warehouse.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testContextValuesWithWarehouse() throws Exception {
        Warehouse mockWarehouse = mock(Warehouse.class);
        when(mockWarehouse.getId()).thenReturn(WAREHOUSE_ID);

        Object cv = newContextValues(createMockContext(mockWarehouse));

        assertEquals(WAREHOUSE_ID, getContextValueField(cv, "warehouseId"));
    }

    // ========== hydrateContext Tests ==========

    /**
     * Verifies hydrateContext does not set context when ContextValues is null.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testHydrateContextWithNullValues() throws Exception {
        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method hydrateMethod = CallAsyncProcess.class.getDeclaredMethod(HYDRATE_CONTEXT_METHOD,
                Class.forName(CONTEXT_VALUES_CLASS));
        hydrateMethod.setAccessible(true);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            // Should not throw when values is null
            hydrateMethod.invoke(instance, (Object) null);

            obContextStatic.verify(
                    () -> OBContext.setOBContext(anyString(), anyString(), anyString(),
                            anyString(), anyString(), anyString()),
                    never());
        }
    }

    /**
     * Verifies hydrateContext does not set context when userId is null.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testHydrateContextWithNullUserId() throws Exception {
        Class<?> cvClass = Class.forName(
                CONTEXT_VALUES_CLASS);
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);

        Object cv = ctor.newInstance((OBContext) null);

        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method hydrateMethod = CallAsyncProcess.class.getDeclaredMethod(HYDRATE_CONTEXT_METHOD,
                cvClass);
        hydrateMethod.setAccessible(true);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            hydrateMethod.invoke(instance, cv);

            obContextStatic.verify(
                    () -> OBContext.setOBContext(anyString(), anyString(), anyString(),
                            anyString(), anyString(), anyString()),
                    never());
        }
    }

    /**
     * Verifies hydrateContext correctly sets OBContext with valid ContextValues.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testHydrateContextWithValidValues() throws Exception {
        Warehouse mockWarehouse = mock(Warehouse.class);
        when(mockWarehouse.getId()).thenReturn(WAREHOUSE_ID);
        Object cv = newContextValues(createMockContext(mockWarehouse));

        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method hydrateMethod = CallAsyncProcess.class.getDeclaredMethod(HYDRATE_CONTEXT_METHOD,
                cv.getClass());
        hydrateMethod.setAccessible(true);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            hydrateMethod.invoke(instance, cv);

            obContextStatic.verify(
                    () -> OBContext.setOBContext(USER_ID, ROLE_ID, CLIENT_ID, ORG_ID,
                            LANG_ID, WAREHOUSE_ID));
        }
    }

    // ========== executeProcedure Tests ==========

    /**
     * Verifies executeProcedure generates correct SQL for PostgreSQL without doCommit.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testExecuteProcedurePostgre() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        OBDal mockOBDal = mock(OBDal.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        OBPropertiesProvider mockPropsProvider = mock(OBPropertiesProvider.class);
        Properties mockProps = new Properties();
        Session mockSession = mock(Session.class);

        mockProcedureExecution(pInstance, process, mockOBDal, mockConnection, mockPs,
                mockPropsProvider, mockProps, mockSession, TEST_PROCEDURE, "POSTGRE",
                "SELECT * FROM test_procedure(?)");
        invokeExecuteProcedure(pInstance, process, null, mockOBDal, mockPropsProvider);

        verify(mockPs).setString(1, PINSTANCE_ID);
        verify(mockPs).execute();
        verify(mockPs).close();
        verify(mockSession).refresh(pInstance);
    }

    /**
     * Verifies executeProcedure generates correct SQL for non-PostgreSQL with doCommit true.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testExecuteProcedureNonPostgreWithDoCommit() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        OBDal mockOBDal = mock(OBDal.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        OBPropertiesProvider mockPropsProvider = mock(OBPropertiesProvider.class);
        Properties mockProps = new Properties();
        Session mockSession = mock(Session.class);

        mockProcedureExecution(pInstance, process, mockOBDal, mockConnection, mockPs,
                mockPropsProvider, mockProps, mockSession, TEST_PROCEDURE, "ORACLE",
                " CALL test_procedure(?,?)");
        invokeExecuteProcedure(pInstance, process, Boolean.TRUE, mockOBDal, mockPropsProvider);

        verify(mockPs).setString(1, PINSTANCE_ID);
        verify(mockPs).setString(2, "Y");
        verify(mockPs).execute();
        verify(mockSession).refresh(pInstance);
    }

    /**
     * Verifies executeProcedure passes doCommit false as "N" parameter.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testExecuteProcedureWithDoCommitFalse() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        OBDal mockOBDal = mock(OBDal.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        OBPropertiesProvider mockPropsProvider = mock(OBPropertiesProvider.class);
        Properties mockProps = new Properties();
        Session mockSession = mock(Session.class);

        mockProcedureExecution(pInstance, process, mockOBDal, mockConnection, mockPs,
                mockPropsProvider, mockProps, mockSession, "my_proc", "POSTGRE",
                "SELECT * FROM my_proc(?,?)");
        invokeExecuteProcedure(pInstance, process, Boolean.FALSE, mockOBDal, mockPropsProvider);

        verify(mockPs).setString(1, PINSTANCE_ID);
        verify(mockPs).setString(2, "N");
        verify(mockPs).execute();
    }

    /**
     * Verifies executeProcedure handles missing rdbms property by defaulting to non-PostgreSQL SQL.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testExecuteProcedureWithNullRdbmsProperty() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        OBDal mockOBDal = mock(OBDal.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        OBPropertiesProvider mockPropsProvider = mock(OBPropertiesProvider.class);
        Properties mockProps = new Properties();
        Session mockSession = mock(Session.class);

        mockProcedureExecution(pInstance, process, mockOBDal, mockConnection, mockPs,
                mockPropsProvider, mockProps, mockSession, TEST_PROCEDURE, null,
                " CALL test_procedure(?)");
        invokeExecuteProcedure(pInstance, process, null, mockOBDal, mockPropsProvider);

        verify(mockPs).setString(1, PINSTANCE_ID);
        verify(mockPs).execute();
    }

    // ========== callProcess with empty parameters map ==========

    /** Verifies callProcess works correctly with an empty parameters map. */
    @Test
    public void testCallProcessWithEmptyParameters() {
        Process process = mock(Process.class);
        ProcessInstance pInstance = mock(ProcessInstance.class);
        OBContext obContext = createMockContext(mockWarehouseWithId());

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);

            OBProvider obProvider = mock(OBProvider.class);
            obProviderStatic.when(OBProvider::getInstance).thenReturn(obProvider);
            when(obProvider.get(ProcessInstance.class)).thenReturn(pInstance);

            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

            when(process.getId()).thenReturn(PROCESS_ID);
            when(pInstance.getId()).thenReturn(PINSTANCE_ID);

            CallAsyncProcess callAsyncProcess = CallAsyncProcess.getInstance();
            ProcessInstance result = callAsyncProcess.callProcess(process, "REC_1",
                    Collections.emptyMap(), false);

            assertNotNull("Result should not be null", result);
            verify(pInstance).setRecordID("REC_1");
            verify(obDal).save(pInstance);
        }
    }

    private Object newContextValues(OBContext context) throws ReflectiveOperationException {
        Constructor<?> ctor = Class.forName(CONTEXT_VALUES_CLASS).getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);
        return ctor.newInstance(context);
    }

    private Object getContextValueField(Object contextValues, String fieldName) throws ReflectiveOperationException {
        Field field = contextValues.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(contextValues);
    }

    private OBContext createMockContext(Warehouse warehouse) {
        OBContext mockContext = mock(OBContext.class);
        User mockUser = mock(User.class);
        Role mockRole = mock(Role.class);
        Client mockClient = mock(Client.class);
        Organization mockOrg = mock(Organization.class);
        Language mockLanguage = mock(Language.class);

        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockContext.getRole()).thenReturn(mockRole);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockContext.getWarehouse()).thenReturn(warehouse);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockRole.getId()).thenReturn(ROLE_ID);
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockOrg.getId()).thenReturn(ORG_ID);
        when(mockLanguage.getLanguage()).thenReturn(LANG_ID);
        return mockContext;
    }

    private Warehouse mockWarehouseWithId() {
        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(WAREHOUSE_ID);
        return warehouse;
    }

    @SuppressWarnings("java:S107")
    private void mockProcedureExecution(ProcessInstance pInstance, Process process,
            OBDal mockOBDal, Connection mockConnection, PreparedStatement mockPs,
            OBPropertiesProvider mockPropsProvider, Properties mockProps,
            Session mockSession, String procedureName, String rdbms,
            String expectedSql) throws Exception {
        when(pInstance.getId()).thenReturn(PINSTANCE_ID);
        when(process.getProcedure()).thenReturn(procedureName);

        if (rdbms != null) {
            mockProps.setProperty(BBDD_RDBMS_PROPERTY, rdbms);
        }
        when(mockPropsProvider.getOpenbravoProperties()).thenReturn(mockProps);
        when(mockOBDal.getConnection(false)).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(expectedSql)).thenReturn(mockPs);
        when(mockOBDal.getSession()).thenReturn(mockSession);
    }

    private void invokeExecuteProcedure(ProcessInstance pInstance, Process process,
            Boolean doCommit, OBDal mockOBDal,
            OBPropertiesProvider mockPropsProvider) throws Exception {
        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method method = CallAsyncProcess.class.getDeclaredMethod(EXECUTE_PROCEDURE_METHOD,
                ProcessInstance.class, Process.class, Boolean.class);
        method.setAccessible(true);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<OBPropertiesProvider> propsStatic = mockStatic(OBPropertiesProvider.class)) {
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            propsStatic.when(OBPropertiesProvider::getInstance).thenReturn(mockPropsProvider);
            method.invoke(instance, pInstance, process, doCommit);
        }
    }

    private void assertRunInBackgroundRollsBack(String pInstanceId, String processId) throws Exception {
        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method method = CallAsyncProcess.class.getDeclaredMethod("runInBackground",
                String.class, String.class,
                Class.forName(CONTEXT_VALUES_CLASS), Boolean.class, Map.class);
        method.setAccessible(true);

        OBDal mockOBDal = mock(OBDal.class);
        when(mockOBDal.get(ProcessInstance.class, pInstanceId)).thenReturn(null);
        when(mockOBDal.get(Process.class, processId)).thenReturn(null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);

            method.invoke(instance, pInstanceId, processId, null, null, null);

            verify(mockOBDal).rollbackAndClose();
        }
    }

    /** Invokes a private instance method of CallAsyncProcess by reflection. */
    private Object invokePrivate(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = CallAsyncProcess.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method.invoke(CallAsyncProcess.getInstance(), args);
    }

    // ========== runInBackground Tests ==========

    /**
     * Verifies runInBackground handles null ProcessInstance by rolling back.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testRunInBackgroundWhenPInstanceNull() throws Exception {
        assertRunInBackgroundRollsBack("NON_EXISTENT", "NON_EXISTENT_PROC");
    }

    /**
     * Verifies runInBackground error handling when ProcessInstance cannot be found after rollback.
     *
     * @throws Exception if reflection or mock setup fails
     */
    @Test
    public void testRunInBackgroundErrorHandlingWithLongMessage() throws Exception {
        assertRunInBackgroundRollsBack("PI_1", "PR_1");
    }

    // ========== dispatch (resolveJavaClassName / hasJavaClass / hasProcedure) Tests ==========

    private static final String JAVA_CLASS = "org.openbravo.service.db.ExportClientProcess";

    private Process mockProcessWithModelObjects(String processClassName, ModelImplementation... modelObjects) {
        Process process = mock(Process.class);
        when(process.getJavaClassName()).thenReturn(processClassName);
        when(process.getADModelImplementationList()).thenReturn(java.util.Arrays.asList(modelObjects));
        return process;
    }

    private ModelImplementation mockModelObject(String action, String className) {
        ModelImplementation modelObject = mock(ModelImplementation.class);
        lenient().when(modelObject.getAction()).thenReturn(action);
        lenient().when(modelObject.getJavaClassName()).thenReturn(className);
        return modelObject;
    }

    /**
     * Verifies resolveJavaClassName prefers the process' own class name.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testResolveJavaClassNameFromProcess() throws Exception {
        Process process = mockProcessWithModelObjects(JAVA_CLASS);
        assertEquals(JAVA_CLASS,
                invokePrivate("resolveJavaClassName", new Class<?>[] { Process.class }, process));
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
                invokePrivate("resolveJavaClassName", new Class<?>[] { Process.class }, process));
    }

    /**
     * Verifies resolveJavaClassName ignores model objects whose action is not 'P'.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testResolveJavaClassNameIgnoresNonProcessAction() throws Exception {
        Process process = mockProcessWithModelObjects(null, mockModelObject("S", JAVA_CLASS));
        assertNull(invokePrivate("resolveJavaClassName", new Class<?>[] { Process.class }, process));
    }

    /**
     * Verifies resolveJavaClassName returns null when there is no class anywhere.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testResolveJavaClassNameNone() throws Exception {
        Process process = mockProcessWithModelObjects(null);
        assertNull(invokePrivate("resolveJavaClassName", new Class<?>[] { Process.class }, process));
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

        assertTrue((Boolean) invokePrivate("hasProcedure", types, withProc));
        assertFalse((Boolean) invokePrivate("hasProcedure", types, emptyProc));
        assertFalse((Boolean) invokePrivate("hasProcedure", types, nullProc));
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
        ProcessInstance pInstance = mock(ProcessInstance.class);
        OBError result = new OBError();
        result.setType("Success");
        result.setMessage("Exported");

        OBDal mockOBDal = mock(OBDal.class);
        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            invokePrivate("applyResultToInstance",
                    new Class<?>[] { ProcessInstance.class, Object.class }, pInstance, result);
        }

        verify(pInstance).setResult(1L);
        verify(pInstance).setErrorMsg("Exported");
    }

    /**
     * Verifies an Error OBError sets result 0 and the message on the instance.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testApplyResultToInstanceError() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        OBError result = new OBError();
        result.setType("Error");
        result.setMessage("Boom");

        OBDal mockOBDal = mock(OBDal.class);
        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            invokePrivate("applyResultToInstance",
                    new Class<?>[] { ProcessInstance.class, Object.class }, pInstance, result);
        }

        verify(pInstance).setResult(0L);
        verify(pInstance).setErrorMsg("Boom");
    }

    /**
     * Verifies a non-OBError result is treated as success.
     *
     * @throws Exception if reflection fails
     */
    @Test
    public void testApplyResultToInstanceNonError() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);

        OBDal mockOBDal = mock(OBDal.class);
        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            invokePrivate("applyResultToInstance",
                    new Class<?>[] { ProcessInstance.class, Object.class }, pInstance, (Object) null);
        }

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
