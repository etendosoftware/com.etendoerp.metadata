package com.etendoerp.metadata.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
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
import org.openbravo.test.base.OBBaseTest;

/**
 * Unit tests for {@link CallAsyncProcess}.
 */
public class CallAsyncProcessTest extends OBBaseTest {

    private static final String USER_ID = "USER_ID";
    private static final String ROLE_ID = "ROLE_ID";
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String ORG_ID = "ORG_ID";
    private static final String LANG_ID = "en_US";
    private static final String WAREHOUSE_ID = "WAREHOUSE_ID";
    private static final String PROCESS_ID = "PROCESS_ID";
    private static final String PINSTANCE_ID = "PINSTANCE_ID";
    private static final String RECORD_ID = "RECORD_ID";
    private static final String STRING_PARAM = "StringParam";
    private static final String DATE_PARAM = "DateParam";
    private static final String DECIMAL_PARAM = "DecimalParam";
    private static final String VALUE_STRING = "Value";
    private static final String ZERO_STRING = "0";

    private static final String EXECUTOR_FIELD = "executorService";

    private Process process;
    private ProcessInstance pInstance;
    private User user;
    private Role role;
    private Client client;
    private Organization org;
    private Language language;
    private Warehouse warehouse;
    private OBContext obContext;
    private CallAsyncProcess callAsyncProcess;
    private ExecutorService originalExecutor;

    @Before
    public void setUpMocks() throws Exception {
        process = mock(Process.class);
        pInstance = mock(ProcessInstance.class);
        user = mock(User.class);
        role = mock(Role.class);
        client = mock(Client.class);
        org = mock(Organization.class);
        language = mock(Language.class);
        warehouse = mock(Warehouse.class);
        obContext = mock(OBContext.class);

        callAsyncProcess = CallAsyncProcess.getInstance();
        Field field = CallAsyncProcess.class.getDeclaredField(EXECUTOR_FIELD);
        field.setAccessible(true);
        originalExecutor = (ExecutorService) field.get(callAsyncProcess);
        field.set(callAsyncProcess, mock(ExecutorService.class));
    }

    @After
    public void tearDownMocks() throws Exception {
        Field field = CallAsyncProcess.class.getDeclaredField(EXECUTOR_FIELD);
        field.setAccessible(true);
        field.set(callAsyncProcess, originalExecutor);
    }

    private void setupStaticMocks(
            MockedStatic<OBContext> obContextStatic,
            MockedStatic<OBProvider> obProviderStatic,
            MockedStatic<OBDal> obDalStatic,
            OBProvider obProvider,
            OBDal obDal) {
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
        obProviderStatic.when(OBProvider::getInstance).thenReturn(obProvider);
        when(obProvider.get(ProcessInstance.class)).thenReturn(pInstance);
        obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
        when(process.getId()).thenReturn(PROCESS_ID);
        when(pInstance.getId()).thenReturn(PINSTANCE_ID);
    }

    /**
     * Tests that getInstance returns the same instance.
     */
    @Test
    public void testGetInstance() {
        CallAsyncProcess instance1 = CallAsyncProcess.getInstance();
        CallAsyncProcess instance2 = CallAsyncProcess.getInstance();

        assertNotNull("Instance should not be null", instance1);
        assertSame("Should return the same instance", instance1, instance2);
    }

    @Test
    public void testCallProcessSyncPhase() {
        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
                MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
                MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            OBProvider obProvider = mock(OBProvider.class);
            OBDal obDal = mock(OBDal.class);
            Parameter parameter = mock(Parameter.class);
            when(obProvider.get(Parameter.class)).thenReturn(parameter);

            setupStaticMocks(obContextStatic, obProviderStatic, obDalStatic, obProvider, obDal);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put(STRING_PARAM, VALUE_STRING);
            parameters.put(DATE_PARAM, new Date());
            parameters.put(DECIMAL_PARAM, new BigDecimal("10.0"));

            ProcessInstance result = callAsyncProcess.callProcess(process, RECORD_ID, parameters, true);

            verify(pInstance).setProcess(process);
            verify(pInstance).setActive(true);
            verify(pInstance).setAllowRead(true);
            verify(pInstance).setRecordID(RECORD_ID);
            verify(pInstance).setUserContact(user);

            verify(parameter).setParameterName(STRING_PARAM);
            verify(parameter).setString(VALUE_STRING);
            verify(parameter).setParameterName(DATE_PARAM);
            verify(parameter, times(1)).setProcessDate(any(Date.class));
            verify(parameter).setParameterName(DECIMAL_PARAM);
            verify(parameter).setProcessNumber(new BigDecimal("10.0"));

            verify(obDal).save(pInstance);
            verify(pInstance).setResult(0L);
            verify(pInstance).setErrorMsg(CallAsyncProcess.PROCESSING_MSG);
            verify(obDal).flush();

            assertEquals(pInstance, result);
        }
    }

    @Test
    public void testCallProcessWithNullWarehouse() {
        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
                MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
                MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            OBProvider obProvider = mock(OBProvider.class);
            OBDal obDal = mock(OBDal.class);

            when(obContext.getWarehouse()).thenReturn(null);

            obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getUser()).thenReturn(user);
            when(obContext.getRole()).thenReturn(role);
            when(obContext.getCurrentClient()).thenReturn(client);
            when(obContext.getCurrentOrganization()).thenReturn(org);
            when(obContext.getLanguage()).thenReturn(language);
            when(user.getId()).thenReturn(USER_ID);
            when(role.getId()).thenReturn(ROLE_ID);
            when(client.getId()).thenReturn(CLIENT_ID);
            when(org.getId()).thenReturn(ORG_ID);
            when(language.getLanguage()).thenReturn(LANG_ID);
            obProviderStatic.when(OBProvider::getInstance).thenReturn(obProvider);
            when(obProvider.get(ProcessInstance.class)).thenReturn(pInstance);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(process.getId()).thenReturn(PROCESS_ID);
            when(pInstance.getId()).thenReturn(PINSTANCE_ID);

            ProcessInstance result = callAsyncProcess.callProcess(process, RECORD_ID, null, true);

            assertNotNull("Result should not be null", result);
            assertEquals(pInstance, result);
        }
    }

    @Test
    public void testRunInBackgroundHappyPath() throws Exception {
        AtomicReference<Runnable> capturedTask = new AtomicReference<>();
        ExecutorService capturingExecutor = mock(ExecutorService.class);
        doAnswer(inv -> {
            capturedTask.set(inv.getArgument(0, Runnable.class));
            return null;
        }).when(capturingExecutor).submit(any(Runnable.class));

        Field execField = CallAsyncProcess.class.getDeclaredField(EXECUTOR_FIELD);
        execField.setAccessible(true);
        ExecutorService prevExecutor = (ExecutorService) execField.get(callAsyncProcess);
        execField.set(callAsyncProcess, capturingExecutor);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
                MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
                MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
                MockedStatic<OBPropertiesProvider> obPropStatic = mockStatic(OBPropertiesProvider.class)) {

            OBProvider obProvider = mock(OBProvider.class);
            OBDal obDal = mock(OBDal.class);
            OBPropertiesProvider propsProvider = mock(OBPropertiesProvider.class);
            Properties props = mock(Properties.class);
            Connection conn = mock(Connection.class);
            PreparedStatement ps = mock(PreparedStatement.class);
            Session hibSession = mock(Session.class);

            setupStaticMocks(obContextStatic, obProviderStatic, obDalStatic, obProvider, obDal);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
            obContextStatic.when(() -> OBContext.setAdminMode()).thenAnswer(inv -> null);
            obContextStatic.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);
            obContextStatic.when(() -> OBContext.setOBContext(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(inv -> null);

            when(obDal.get(ProcessInstance.class, PINSTANCE_ID)).thenReturn(pInstance);
            when(obDal.get(Process.class, PROCESS_ID)).thenReturn(process);
            when(obDal.getConnection(false)).thenReturn(conn);
            when(obDal.getSession()).thenReturn(hibSession);
            doNothing().when(obDal).commitAndClose();

            when(process.getProcedure()).thenReturn("MY_PROC");
            when(pInstance.getId()).thenReturn(PINSTANCE_ID);

            obPropStatic.when(OBPropertiesProvider::getInstance).thenReturn(propsProvider);
            when(propsProvider.getOpenbravoProperties()).thenReturn(props);
            when(props.getProperty("bbdd.rdbms")).thenReturn("POSTGRE");
            when(conn.prepareStatement(anyString())).thenReturn(ps);

            callAsyncProcess.callProcess(process, RECORD_ID, null, null);

            assertNotNull("Background task should have been captured", capturedTask.get());
            capturedTask.get().run();

            verify(obDal).commitAndClose();
            verify(ps).execute();
        } finally {
            execField.set(callAsyncProcess, prevExecutor);
        }
    }

    @Test
    public void testRunInBackgroundWithProcessNotFound() throws Exception {
        AtomicReference<Runnable> capturedTask = new AtomicReference<>();
        ExecutorService capturingExecutor = mock(ExecutorService.class);
        doAnswer(inv -> {
            capturedTask.set(inv.getArgument(0, Runnable.class));
            return null;
        }).when(capturingExecutor).submit(any(Runnable.class));

        Field execField = CallAsyncProcess.class.getDeclaredField(EXECUTOR_FIELD);
        execField.setAccessible(true);
        ExecutorService prevExecutor = (ExecutorService) execField.get(callAsyncProcess);
        execField.set(callAsyncProcess, capturingExecutor);

        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
                MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
                MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            OBProvider obProvider = mock(OBProvider.class);
            OBDal obDal = mock(OBDal.class);

            setupStaticMocks(obContextStatic, obProviderStatic, obDalStatic, obProvider, obDal);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
            obContextStatic.when(() -> OBContext.setAdminMode()).thenAnswer(inv -> null);
            obContextStatic.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);
            obContextStatic.when(() -> OBContext.setOBContext(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(inv -> null);

            // Both process and pInstance return null → OBException in runInBackground
            when(obDal.get(ProcessInstance.class, PINSTANCE_ID)).thenReturn(null);
            when(obDal.get(Process.class, PROCESS_ID)).thenReturn(null);
            doNothing().when(obDal).rollbackAndClose();
            doNothing().when(obDal).commitAndClose();

            callAsyncProcess.callProcess(process, RECORD_ID, null, true);

            assertNotNull("Background task should have been captured", capturedTask.get());
            capturedTask.get().run();

            verify(obDal).rollbackAndClose();
            verify(obDal, never()).commitAndClose();
        } finally {
            execField.set(callAsyncProcess, prevExecutor);
        }
    }

    @Test
    public void testCallProcessWithNullRecordId() {
        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
                MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
                MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            OBProvider obProvider = mock(OBProvider.class);
            OBDal obDal = mock(OBDal.class);

            setupStaticMocks(obContextStatic, obProviderStatic, obDalStatic, obProvider, obDal);

            ProcessInstance result = callAsyncProcess.callProcess(process, null, null, true);

            verify(pInstance).setRecordID(ZERO_STRING);
            assertEquals(pInstance, result);
        }
    }
}
