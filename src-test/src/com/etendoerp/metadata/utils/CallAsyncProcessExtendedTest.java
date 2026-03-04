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

    private static final String USER_ID = "USER_ID";
    private static final String ROLE_ID = "ROLE_ID";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String ORG_ID = "ORG_ID";
    private static final String LANG_ID = "en_US";
    private static final String WAREHOUSE_ID = "WAREHOUSE_ID";
    private static final String PINSTANCE_ID = "PI_ID";
    private static final String PROCESS_ID = "PROC_ID";

    // ========== ContextValues Tests ==========

    @Test
    public void testContextValuesWithNullContext() throws Exception {
        Class<?> cvClass = Class.forName(
                "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues");
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);

        Object cv = ctor.newInstance((OBContext) null);

        Field userIdField = cvClass.getDeclaredField("userId");
        userIdField.setAccessible(true);
        assertNull("userId should be null when context is null", userIdField.get(cv));
    }

    @Test
    public void testContextValuesWithNullWarehouse() throws Exception {
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
        when(mockContext.getWarehouse()).thenReturn(null);

        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockRole.getId()).thenReturn(ROLE_ID);
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockOrg.getId()).thenReturn(ORG_ID);
        when(mockLanguage.getLanguage()).thenReturn(LANG_ID);

        Class<?> cvClass = Class.forName(
                "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues");
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);

        Object cv = ctor.newInstance(mockContext);

        Field warehouseField = cvClass.getDeclaredField("warehouseId");
        warehouseField.setAccessible(true);
        assertNull("warehouseId should be null when warehouse is null",
                warehouseField.get(cv));

        Field userIdField = cvClass.getDeclaredField("userId");
        userIdField.setAccessible(true);
        assertEquals(USER_ID, userIdField.get(cv));
    }

    @Test
    public void testContextValuesWithWarehouse() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        User mockUser = mock(User.class);
        Role mockRole = mock(Role.class);
        Client mockClient = mock(Client.class);
        Organization mockOrg = mock(Organization.class);
        Language mockLanguage = mock(Language.class);
        Warehouse mockWarehouse = mock(Warehouse.class);

        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockContext.getRole()).thenReturn(mockRole);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockContext.getWarehouse()).thenReturn(mockWarehouse);

        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockRole.getId()).thenReturn(ROLE_ID);
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockOrg.getId()).thenReturn(ORG_ID);
        when(mockLanguage.getLanguage()).thenReturn(LANG_ID);
        when(mockWarehouse.getId()).thenReturn(WAREHOUSE_ID);

        Class<?> cvClass = Class.forName(
                "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues");
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);

        Object cv = ctor.newInstance(mockContext);

        Field warehouseField = cvClass.getDeclaredField("warehouseId");
        warehouseField.setAccessible(true);
        assertEquals(WAREHOUSE_ID, warehouseField.get(cv));
    }

    // ========== hydrateContext Tests ==========

    @Test
    public void testHydrateContextWithNullValues() throws Exception {
        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method hydrateMethod = CallAsyncProcess.class.getDeclaredMethod("hydrateContext",
                Class.forName("com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues"));
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

    @Test
    public void testHydrateContextWithNullUserId() throws Exception {
        Class<?> cvClass = Class.forName(
                "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues");
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);

        Object cv = ctor.newInstance((OBContext) null);

        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method hydrateMethod = CallAsyncProcess.class.getDeclaredMethod("hydrateContext",
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

    @Test
    public void testHydrateContextWithValidValues() throws Exception {
        OBContext mockContext = mock(OBContext.class);
        User mockUser = mock(User.class);
        Role mockRole = mock(Role.class);
        Client mockClient = mock(Client.class);
        Organization mockOrg = mock(Organization.class);
        Language mockLanguage = mock(Language.class);
        Warehouse mockWarehouse = mock(Warehouse.class);

        when(mockContext.getUser()).thenReturn(mockUser);
        when(mockContext.getRole()).thenReturn(mockRole);
        when(mockContext.getCurrentClient()).thenReturn(mockClient);
        when(mockContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(mockContext.getLanguage()).thenReturn(mockLanguage);
        when(mockContext.getWarehouse()).thenReturn(mockWarehouse);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockRole.getId()).thenReturn(ROLE_ID);
        when(mockClient.getId()).thenReturn(CLIENT_ID);
        when(mockOrg.getId()).thenReturn(ORG_ID);
        when(mockLanguage.getLanguage()).thenReturn(LANG_ID);
        when(mockWarehouse.getId()).thenReturn(WAREHOUSE_ID);

        Class<?> cvClass = Class.forName(
                "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues");
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);
        Object cv = ctor.newInstance(mockContext);

        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method hydrateMethod = CallAsyncProcess.class.getDeclaredMethod("hydrateContext",
                cvClass);
        hydrateMethod.setAccessible(true);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class)) {
            hydrateMethod.invoke(instance, cv);

            obContextStatic.verify(
                    () -> OBContext.setOBContext(USER_ID, ROLE_ID, CLIENT_ID, ORG_ID,
                            LANG_ID, WAREHOUSE_ID));
        }
    }

    // ========== executeProcedure Tests ==========

    @Test
    public void testExecuteProcedurePostgre() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        OBDal mockOBDal = mock(OBDal.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        OBPropertiesProvider mockPropsProvider = mock(OBPropertiesProvider.class);
        Properties mockProps = new Properties();
        mockProps.setProperty("bbdd.rdbms", "POSTGRE");
        Session mockSession = mock(Session.class);

        when(process.getProcedure()).thenReturn("test_procedure");
        when(pInstance.getId()).thenReturn(PINSTANCE_ID);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<OBPropertiesProvider> propsStatic = mockStatic(
                     OBPropertiesProvider.class)) {

            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            propsStatic.when(OBPropertiesProvider::getInstance).thenReturn(mockPropsProvider);

            when(mockOBDal.getConnection(false)).thenReturn(mockConnection);
            when(mockPropsProvider.getOpenbravoProperties()).thenReturn(mockProps);
            when(mockConnection.prepareStatement(
                    "SELECT * FROM test_procedure(?)")).thenReturn(mockPs);
            when(mockOBDal.getSession()).thenReturn(mockSession);

            CallAsyncProcess instance = CallAsyncProcess.getInstance();
            Method execMethod = CallAsyncProcess.class.getDeclaredMethod("executeProcedure",
                    ProcessInstance.class, Process.class, Boolean.class);
            execMethod.setAccessible(true);

            execMethod.invoke(instance, pInstance, process, null);

            verify(mockPs).setString(1, PINSTANCE_ID);
            verify(mockPs).execute();
            verify(mockPs).close();
            verify(mockSession).refresh(pInstance);
        }
    }

    @Test
    public void testExecuteProcedureNonPostgreWithDoCommit() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        OBDal mockOBDal = mock(OBDal.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        OBPropertiesProvider mockPropsProvider = mock(OBPropertiesProvider.class);
        Properties mockProps = new Properties();
        mockProps.setProperty("bbdd.rdbms", "ORACLE");
        Session mockSession = mock(Session.class);

        when(process.getProcedure()).thenReturn("test_procedure");
        when(pInstance.getId()).thenReturn(PINSTANCE_ID);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<OBPropertiesProvider> propsStatic = mockStatic(
                     OBPropertiesProvider.class)) {

            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            propsStatic.when(OBPropertiesProvider::getInstance).thenReturn(mockPropsProvider);

            when(mockOBDal.getConnection(false)).thenReturn(mockConnection);
            when(mockPropsProvider.getOpenbravoProperties()).thenReturn(mockProps);
            when(mockConnection.prepareStatement(
                    " CALL test_procedure(?,?)")).thenReturn(mockPs);
            when(mockOBDal.getSession()).thenReturn(mockSession);

            CallAsyncProcess instance = CallAsyncProcess.getInstance();
            Method execMethod = CallAsyncProcess.class.getDeclaredMethod("executeProcedure",
                    ProcessInstance.class, Process.class, Boolean.class);
            execMethod.setAccessible(true);

            execMethod.invoke(instance, pInstance, process, Boolean.TRUE);

            verify(mockPs).setString(1, PINSTANCE_ID);
            verify(mockPs).setString(2, "Y");
            verify(mockPs).execute();
            verify(mockSession).refresh(pInstance);
        }
    }

    @Test
    public void testExecuteProcedureWithDoCommitFalse() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        OBDal mockOBDal = mock(OBDal.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        OBPropertiesProvider mockPropsProvider = mock(OBPropertiesProvider.class);
        Properties mockProps = new Properties();
        mockProps.setProperty("bbdd.rdbms", "POSTGRE");
        Session mockSession = mock(Session.class);

        when(process.getProcedure()).thenReturn("my_proc");
        when(pInstance.getId()).thenReturn(PINSTANCE_ID);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<OBPropertiesProvider> propsStatic = mockStatic(
                     OBPropertiesProvider.class)) {

            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            propsStatic.when(OBPropertiesProvider::getInstance).thenReturn(mockPropsProvider);

            when(mockOBDal.getConnection(false)).thenReturn(mockConnection);
            when(mockPropsProvider.getOpenbravoProperties()).thenReturn(mockProps);
            when(mockConnection.prepareStatement(
                    "SELECT * FROM my_proc(?,?)")).thenReturn(mockPs);
            when(mockOBDal.getSession()).thenReturn(mockSession);

            CallAsyncProcess instance = CallAsyncProcess.getInstance();
            Method execMethod = CallAsyncProcess.class.getDeclaredMethod("executeProcedure",
                    ProcessInstance.class, Process.class, Boolean.class);
            execMethod.setAccessible(true);

            execMethod.invoke(instance, pInstance, process, Boolean.FALSE);

            verify(mockPs).setString(1, PINSTANCE_ID);
            verify(mockPs).setString(2, "N");
            verify(mockPs).execute();
        }
    }

    @Test
    public void testExecuteProcedureWithNullRdbmsProperty() throws Exception {
        ProcessInstance pInstance = mock(ProcessInstance.class);
        Process process = mock(Process.class);
        OBDal mockOBDal = mock(OBDal.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPs = mock(PreparedStatement.class);
        OBPropertiesProvider mockPropsProvider = mock(OBPropertiesProvider.class);
        Properties mockProps = new Properties();
        // No bbdd.rdbms property set
        Session mockSession = mock(Session.class);

        when(process.getProcedure()).thenReturn("test_procedure");
        when(pInstance.getId()).thenReturn(PINSTANCE_ID);

        try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
             MockedStatic<OBPropertiesProvider> propsStatic = mockStatic(
                     OBPropertiesProvider.class)) {

            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            propsStatic.when(OBPropertiesProvider::getInstance).thenReturn(mockPropsProvider);

            when(mockOBDal.getConnection(false)).thenReturn(mockConnection);
            when(mockPropsProvider.getOpenbravoProperties()).thenReturn(mockProps);
            when(mockConnection.prepareStatement(
                    " CALL test_procedure(?)")).thenReturn(mockPs);
            when(mockOBDal.getSession()).thenReturn(mockSession);

            CallAsyncProcess instance = CallAsyncProcess.getInstance();
            Method execMethod = CallAsyncProcess.class.getDeclaredMethod("executeProcedure",
                    ProcessInstance.class, Process.class, Boolean.class);
            execMethod.setAccessible(true);

            execMethod.invoke(instance, pInstance, process, (Boolean) null);

            verify(mockPs).setString(1, PINSTANCE_ID);
            verify(mockPs).execute();
        }
    }

    // ========== callProcess with empty parameters map ==========

    @Test
    public void testCallProcessWithEmptyParameters() {
        Process process = mock(Process.class);
        ProcessInstance pInstance = mock(ProcessInstance.class);
        User user = mock(User.class);
        Role role = mock(Role.class);
        Client client = mock(Client.class);
        Organization org = mock(Organization.class);
        Language language = mock(Language.class);
        Warehouse warehouse = mock(Warehouse.class);
        OBContext obContext = mock(OBContext.class);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getUser()).thenReturn(user);
            when(obContext.getRole()).thenReturn(role);
            when(obContext.getCurrentClient()).thenReturn(client);
            when(obContext.getCurrentOrganization()).thenReturn(org);
            when(obContext.getLanguage()).thenReturn(language);
            when(obContext.getWarehouse()).thenReturn(warehouse);

            when(user.getId()).thenReturn(USER_ID);
            when(role.getId()).thenReturn(ROLE_ID);
            when(client.getId()).thenReturn(CLIENT_ID);
            when(org.getId()).thenReturn(ORG_ID);
            when(language.getLanguage()).thenReturn(LANG_ID);
            when(warehouse.getId()).thenReturn(WAREHOUSE_ID);

            OBProvider obProvider = mock(OBProvider.class);
            obProviderStatic.when(OBProvider::getInstance).thenReturn(obProvider);
            when(obProvider.get(ProcessInstance.class)).thenReturn(pInstance);

            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

            when(process.getId()).thenReturn(PROCESS_ID);
            when(pInstance.getId()).thenReturn(PINSTANCE_ID);

            // Empty parameters map
            Map<String, Object> emptyParams = Collections.emptyMap();

            CallAsyncProcess callAsyncProcess = CallAsyncProcess.getInstance();
            ProcessInstance result = callAsyncProcess.callProcess(process, "REC_1",
                    emptyParams, false);

            assertNotNull("Result should not be null", result);
            verify(pInstance).setRecordID("REC_1");
            verify(obDal).save(pInstance);
        }
    }

    // ========== runInBackground Tests ==========

    @Test
    public void testRunInBackgroundWhenPInstanceNull() throws Exception {
        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method runMethod = CallAsyncProcess.class.getDeclaredMethod("runInBackground",
                String.class, String.class,
                Class.forName(
                        "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues"),
                Boolean.class);
        runMethod.setAccessible(true);

        // Create ContextValues with null context
        Class<?> cvClass = Class.forName(
                "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues");
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);
        Object cv = ctor.newInstance((OBContext) null);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            OBDal mockOBDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.get(ProcessInstance.class, "NON_EXISTENT")).thenReturn(null);
            lenient().when(mockOBDal.get(Process.class, "NON_EXISTENT_PROC")).thenReturn(null);

            // Should not throw - error is caught and logged
            runMethod.invoke(instance, "NON_EXISTENT", "NON_EXISTENT_PROC", cv,
                    Boolean.TRUE);

            // Verify rollback was attempted since OBException was thrown
            verify(mockOBDal).rollbackAndClose();
        }
    }

    @Test
    public void testRunInBackgroundErrorHandlingWithLongMessage() throws Exception {
        CallAsyncProcess instance = CallAsyncProcess.getInstance();
        Method runMethod = CallAsyncProcess.class.getDeclaredMethod("runInBackground",
                String.class, String.class,
                Class.forName(
                        "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues"),
                Boolean.class);
        runMethod.setAccessible(true);

        Class<?> cvClass = Class.forName(
                "com.etendoerp.metadata.utils.CallAsyncProcess$ContextValues");
        Constructor<?> ctor = cvClass.getDeclaredConstructor(OBContext.class);
        ctor.setAccessible(true);
        Object cv = ctor.newInstance((OBContext) null);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
             MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            OBDal mockOBDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);
            when(mockOBDal.get(ProcessInstance.class, "PI_1")).thenReturn(null);
            lenient().when(mockOBDal.get(Process.class, "PR_1")).thenReturn(null);

            // After rollbackAndClose, a new OBDal call happens
            ProcessInstance recoveryPi = mock(ProcessInstance.class);
            // On second call after rollback
            when(mockOBDal.get(ProcessInstance.class, "PI_1")).thenReturn(null)
                    .thenReturn(recoveryPi);

            runMethod.invoke(instance, "PI_1", "PR_1", cv, Boolean.TRUE);

            verify(mockOBDal).rollbackAndClose();
        }
    }
}
