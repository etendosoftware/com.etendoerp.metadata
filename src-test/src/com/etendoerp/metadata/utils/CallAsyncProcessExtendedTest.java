package com.etendoerp.metadata.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import org.openbravo.model.ad.process.Parameter;
import org.openbravo.model.ad.process.ProcessInstance;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.enterprise.Warehouse;

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
                Class.forName(CONTEXT_VALUES_CLASS), Boolean.class);
        method.setAccessible(true);

        OBDal mockOBDal = mock(OBDal.class);
        when(mockOBDal.get(ProcessInstance.class, pInstanceId)).thenReturn(null);
        when(mockOBDal.get(Process.class, processId)).thenReturn(null);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);

            method.invoke(instance, pInstanceId, processId, null, null);

            verify(mockOBDal).rollbackAndClose();
        }
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
}
