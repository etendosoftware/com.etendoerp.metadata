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
 * All portions are Copyright © 2021-2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.metadata.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.util.concurrent.MoreExecutors;
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

    private Process process;
    private ProcessInstance pInstance;
    private User user;
    private Role role;
    private Client client;
    private Organization org;
    private Language language;
    private Warehouse warehouse;
    private OBContext obContext;
    private Parameter parameter;

    private ExecutorService originalExecutor;

    /**
     * Sets up the test environment.
     * Uses a direct executor service to run background tasks synchronously in tests.
     */
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        originalExecutor = CallAsyncProcess.getInstance().getExecutorService();
        CallAsyncProcess.getInstance().setExecutorService(MoreExecutors.newDirectExecutorService());

        // Initialize common mocks
        process = mock(Process.class);
        pInstance = mock(ProcessInstance.class);
        user = mock(User.class);
        role = mock(Role.class);
        client = mock(Client.class);
        org = mock(Organization.class);
        language = mock(Language.class);
        warehouse = mock(Warehouse.class);
        obContext = mock(OBContext.class);
        parameter = mock(Parameter.class);
    }

    /**
     * Helper to setup OBContext and identity mocks.
     * Reduces repetition of basic configuration across test methods.
     */
    private void setupOBContextMock(MockedStatic<OBContext> obContextStatic) {
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
    }

    /**
     * Cleans up the test environment.
     * Restores the original executor service.
     */
    @After
    public void tearDown() {
        if (originalExecutor != null) {
            CallAsyncProcess.getInstance().setExecutorService(originalExecutor);
        }
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

            setupOBContextMock(obContextStatic);

            // Mock OBProvider
            OBProvider obProvider = mock(OBProvider.class);
            obProviderStatic.when(OBProvider::getInstance).thenReturn(obProvider);
            when(obProvider.get(ProcessInstance.class)).thenReturn(pInstance);
            when(obProvider.get(Parameter.class)).thenReturn(parameter);

            // Mock OBDal
            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

            // Mock Process
            when(process.getId()).thenReturn(PROCESS_ID);
            when(pInstance.getId()).thenReturn(PINSTANCE_ID);

            // Prepare inputs
            Map<String, Object> parametersMap = new HashMap<>();
            parametersMap.put(STRING_PARAM, VALUE_STRING);
            parametersMap.put(DATE_PARAM, new Date());
            parametersMap.put(DECIMAL_PARAM, new BigDecimal("10.0"));

            // Execute
            CallAsyncProcess.getInstance().callProcess(process, RECORD_ID, parametersMap, true);

            // Verify
            verify(pInstance).setProcess(process);
            verify(pInstance).setActive(true);
            verify(pInstance).setAllowRead(true);
            verify(pInstance).setRecordID(RECORD_ID);
            verify(pInstance).setUserContact(user);

            // Verify parameters were added
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
        }
    }

    @Test
    public void testCallProcessWithNullRecordId() {
        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
                MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
                MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {

            setupOBContextMock(obContextStatic);

            // Mock OBProvider
            OBProvider obProvider = mock(OBProvider.class);
            obProviderStatic.when(OBProvider::getInstance).thenReturn(obProvider);
            when(obProvider.get(ProcessInstance.class)).thenReturn(pInstance);

            // Mock OBDal
            OBDal obDal = mock(OBDal.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

            // Mock Process
            when(process.getId()).thenReturn(PROCESS_ID);
            when(pInstance.getId()).thenReturn(PINSTANCE_ID);

            // Execute
            CallAsyncProcess.getInstance().callProcess(process, null, null, true);

            // Verify
            verify(pInstance).setRecordID(ZERO_STRING);
        }
    }

    /**
     * Tests the full execution flow, including the background phase.
     * Since we use a direct executor, the background phase runs synchronously.
     */
    @Test
    public void testFullExecutionFlow() throws Exception {
        try (MockedStatic<OBContext> obContextStatic = mockStatic(OBContext.class);
                MockedStatic<OBProvider> obProviderStatic = mockStatic(OBProvider.class);
                MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
                MockedStatic<OBPropertiesProvider> obPropsProviderStatic = mockStatic(OBPropertiesProvider.class)) {

            setupOBContextMock(obContextStatic);

            // Mock OBProvider
            OBProvider obProvider = mock(OBProvider.class);
            obProviderStatic.when(OBProvider::getInstance).thenReturn(obProvider);
            when(obProvider.get(ProcessInstance.class)).thenReturn(pInstance);

            // Mock OBDal
            OBDal obDal = mock(OBDal.class);
            org.hibernate.Session session = mock(org.hibernate.Session.class);
            obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.getSession()).thenReturn(session);
            when(obDal.get(ProcessInstance.class, PINSTANCE_ID)).thenReturn(pInstance);
            when(obDal.get(Process.class, PROCESS_ID)).thenReturn(process);

            // Mock Connection and Statement
            java.sql.Connection conn = mock(java.sql.Connection.class);
            java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
            when(obDal.getConnection(false)).thenReturn(conn);
            when(conn.prepareStatement(any())).thenReturn(ps);

            // Mock OBProperties
            OBPropertiesProvider obPropsProvider = mock(OBPropertiesProvider.class);
            java.util.Properties obProps = new java.util.Properties();
            obProps.setProperty("bbdd.rdbms", "POSTGRE");
            obPropsProviderStatic.when(OBPropertiesProvider::getInstance).thenReturn(obPropsProvider);
            when(obPropsProvider.getOpenbravoProperties()).thenReturn(obProps);

            // Mock IDs
            when(process.getId()).thenReturn(PROCESS_ID);
            when(process.getProcedure()).thenReturn("test_procedure");
            when(pInstance.getId()).thenReturn(PINSTANCE_ID);

            // Execute
            CallAsyncProcess.getInstance().callProcess(process, RECORD_ID, null, true);

            // Verify
            verify(ps).execute();
            verify(obDal).commitAndClose();
        }
    }
}
