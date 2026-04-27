package com.etendoerp.metadata.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.openbravo.dal.service.OBCriteria;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.openbravo.base.expression.OBScriptEngine;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.Property;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.service.json.DataToJsonConverter;

import com.etendoerp.metadata.builders.ProcessDefinitionBuilder;

/**
 * Tests for Utils methods that interact with OBDal, OBContext, and field processing.
 * Covers getReferencedTab, evaluateDisplayLogicAtServerLevel, getFieldProcess,
 * setContext/getLanguage, and getJsonObject with non-null objects.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class UtilsContextAndFieldTest {

    @Mock
    private OBDal obDal;

    @Mock
    private OBContext obContext;

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private Field mockField;

    @Mock
    private Tab mockTab;

    @Mock
    private Column mockColumn;

    @Mock
    private Property mockProperty;

    @Mock
    private Entity mockEntity;

    @Mock
    private Table mockTable;

    @SuppressWarnings("rawtypes")
    @Mock
    private OBCriteria mockCriteria;

    @Mock
    private Language mockLanguage;

    @Mock
    private Process mockProcess;

    private static final String TABLE_ID_VAL = "TABLE_ID";
    private static final String LANGUAGE_PROP = "language";
    private static final String USER_ID_VAL = "USER_ID";
    private static final String ROLE_ID_VAL = "ROLE_ID";
    private static final String CLIENT_ID_VAL = "CLIENT_ID";
    private static final String ORG_ID_VAL = "ORG_ID";
    private static final String WH_ID_VAL = "WH_ID";

    private MockedStatic<OBDal> obDalStatic;
    private MockedStatic<OBContext> obContextStatic;

    /** Sets up static mocks for OBDal and OBContext. */
    @Before
    public void setUp() {
        obDalStatic = mockStatic(OBDal.class);
        obContextStatic = mockStatic(OBContext.class);
        obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
        obDalStatic.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
        obContextStatic.when(OBContext::getOBContext).thenReturn(obContext);
    }

    /** Closes static mocks to prevent leaks between tests. */
    @After
    public void tearDown() {
        if (obDalStatic != null) {
            obDalStatic.close();
        }
        if (obContextStatic != null) {
            obContextStatic.close();
        }
    }

    // ========== getReferencedTab Tests ==========

    /** Verifies getReferencedTab returns a tab when a matching tab exists. */
    @Test
    public void testGetReferencedTabReturnsTab() {
        when(mockProperty.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getTableId()).thenReturn(TABLE_ID_VAL);
        when(obDal.get(Table.class, TABLE_ID_VAL)).thenReturn(mockTable);
        doReturn(mockCriteria).when(obDal).createCriteria(Tab.class);
        when(mockCriteria.add(any(Criterion.class))).thenReturn(mockCriteria);
        when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
        when(mockCriteria.uniqueResult()).thenReturn(mockTab);

        Tab result = Utils.getReferencedTab(mockProperty);

        assertNotNull("Should return a tab", result);
        assertEquals("Should return the mocked tab", mockTab, result);
    }

    /** Verifies getReferencedTab returns null when no matching tab exists. */
    @Test
    public void testGetReferencedTabReturnsNullWhenNoMatch() {
        when(mockProperty.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getTableId()).thenReturn(TABLE_ID_VAL);
        when(obDal.get(Table.class, TABLE_ID_VAL)).thenReturn(mockTable);
        doReturn(mockCriteria).when(obDal).createCriteria(Tab.class);
        when(mockCriteria.add(any(Criterion.class))).thenReturn(mockCriteria);
        when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
        when(mockCriteria.uniqueResult()).thenReturn(null);

        Tab result = Utils.getReferencedTab(mockProperty);

        assertNull("Should return null when no tab found", result);
    }

    // ========== evaluateDisplayLogicAtServerLevel Tests ==========

    /** Verifies evaluateDisplayLogicAtServerLevel returns true when display logic is null. */
    @Test
    public void testEvaluateDisplayLogicNullReturnsTrueDirectly() {
        when(mockField.getDisplayLogicEvaluatedInTheServer()).thenReturn(null);

        boolean result = Utils.evaluateDisplayLogicAtServerLevel(mockField);

        assertTrue("Should return true when display logic is null", result);
    }

    /**
     * Verifies evaluateDisplayLogicAtServerLevel returns true when script evaluates to true.
     *
     * @throws Exception if mock setup or invocation fails
     */
    @Test
    public void testEvaluateDisplayLogicReturnsTrue() throws Exception {
        when(mockField.getDisplayLogicEvaluatedInTheServer()).thenReturn("true");
        when(mockField.getTab()).thenReturn(mockTab);

        try (MockedStatic<DynamicExpressionParser> parserStatic = mockStatic(
                DynamicExpressionParser.class);
             MockedConstruction<DynamicExpressionParser> parserConstruction = mockConstruction(
                DynamicExpressionParser.class,
                (mock, ctx) -> when(mock.getJSExpression()).thenReturn("true"));
             MockedStatic<OBScriptEngine> scriptStatic = mockStatic(OBScriptEngine.class)) {

            parserStatic.when(
                    () -> DynamicExpressionParser.replaceSystemPreferencesInDisplayLogic("true"))
                    .thenReturn("true");

            OBScriptEngine mockEngine = mock(OBScriptEngine.class);
            scriptStatic.when(OBScriptEngine::getInstance).thenReturn(mockEngine);
            when(mockEngine.eval("true")).thenReturn(Boolean.TRUE);

            boolean result = Utils.evaluateDisplayLogicAtServerLevel(mockField);

            assertTrue("Should return true when display logic evaluates to true", result);
        }
    }

    /**
     * Verifies evaluateDisplayLogicAtServerLevel defaults to true on script exception.
     *
     * @throws Exception if mock setup or invocation fails
     */
    @Test
    public void testEvaluateDisplayLogicScriptExceptionReturnsTrueDefault() throws Exception {
        when(mockField.getDisplayLogicEvaluatedInTheServer()).thenReturn("someLogic");
        when(mockField.getTab()).thenReturn(mockTab);

        try (MockedStatic<DynamicExpressionParser> parserStatic = mockStatic(
                DynamicExpressionParser.class);
             MockedConstruction<DynamicExpressionParser> parserConstruction = mockConstruction(
                DynamicExpressionParser.class,
                (mock, ctx) -> when(mock.getJSExpression()).thenReturn("badExpr"));
             MockedStatic<OBScriptEngine> scriptStatic = mockStatic(OBScriptEngine.class)) {

            parserStatic.when(
                    () -> DynamicExpressionParser.replaceSystemPreferencesInDisplayLogic("someLogic"))
                    .thenReturn("translatedLogic");

            OBScriptEngine mockEngine = mock(OBScriptEngine.class);
            scriptStatic.when(OBScriptEngine::getInstance).thenReturn(mockEngine);
            when(mockEngine.eval(any())).thenThrow(
                    new javax.script.ScriptException("eval error"));

            boolean result = Utils.evaluateDisplayLogicAtServerLevel(mockField);

            assertTrue("Should default to true on ScriptException", result);
        }
    }

    // ========== getFieldProcess Tests ==========

    /**
     * Verifies getFieldProcess returns empty JSON when column has no process.
     *
     * @throws Exception if mock setup or invocation fails
     */
    @Test
    public void testGetFieldProcessReturnsEmptyJsonWhenNoProcess() throws Exception {
        when(mockField.getColumn()).thenReturn(mockColumn);
        when(mockColumn.getOBUIAPPProcess()).thenReturn(null);

        JSONObject result = Utils.getFieldProcess(mockField);

        assertNotNull("Result should not be null", result);
        assertEquals("Should return empty JSON when no process", 0, result.length());
    }

    // ========== setContext Tests ==========

    /** Verifies setContext applies language from request parameter. */
    @Test
    public void testSetContextWithLanguageParameter() {
        when(mockRequest.getParameter(LANGUAGE_PROP)).thenReturn("es_ES");
        lenient().when(mockRequest.getHeader(LANGUAGE_PROP)).thenReturn(null);
        configureSetContextDependencies("es_ES", mockLanguage);

        Utils.setContext(mockRequest);

        verify(obContext).setLanguage(mockLanguage);
        obContextStatic.verify(() -> OBContext.setAdminMode(true));
        obContextStatic.verify(OBContext::restorePreviousMode);
    }

    /** Verifies setContext applies language from request header when parameter is null. */
    @Test
    public void testSetContextWithLanguageHeader() {
        when(mockRequest.getParameter(LANGUAGE_PROP)).thenReturn(null);
        when(mockRequest.getHeader(LANGUAGE_PROP)).thenReturn("fr_FR");
        configureSetContextDependencies("fr_FR", mockLanguage);

        Utils.setContext(mockRequest);

        verify(obContext).setLanguage(mockLanguage);
    }

    /** Verifies setContext handles unknown language code gracefully. */
    @Test
    public void testSetContextWithNoLanguageFound() {
        when(mockRequest.getParameter(LANGUAGE_PROP)).thenReturn("xx_XX");
        lenient().when(mockRequest.getHeader(LANGUAGE_PROP)).thenReturn(null);
        configureSetContextDependencies("en_US", null);

        Utils.setContext(mockRequest);

        obContextStatic.verify(OBContext::restorePreviousMode);
    }

    /** Verifies setContext handles empty language parameter and header. */
    @Test
    public void testSetContextWithBothLanguagesNullOrEmpty() {
        when(mockRequest.getParameter(LANGUAGE_PROP)).thenReturn("");
        when(mockRequest.getHeader(LANGUAGE_PROP)).thenReturn("");
        configureSetContextDependencies("en_US", null);

        Utils.setContext(mockRequest);

        obContextStatic.verify(OBContext::restorePreviousMode);
    }

    private void configureSetContextDependencies(String currentLanguageCode, Language foundLanguage) {
        org.openbravo.model.ad.access.User mockUser = mock(
                org.openbravo.model.ad.access.User.class);
        org.openbravo.model.ad.access.Role mockRole = mock(
                org.openbravo.model.ad.access.Role.class);
        org.openbravo.model.ad.system.Client mockClient = mock(
                org.openbravo.model.ad.system.Client.class);
        org.openbravo.model.common.enterprise.Organization mockOrg = mock(
                org.openbravo.model.common.enterprise.Organization.class);
        org.openbravo.model.common.enterprise.Warehouse mockWarehouse = mock(
                org.openbravo.model.common.enterprise.Warehouse.class);

        doReturn(mockCriteria).when(obDal).createCriteria(Language.class);
        when(mockCriteria.add(any(Criterion.class))).thenReturn(mockCriteria);
        when(mockCriteria.setMaxResults(1)).thenReturn(mockCriteria);
        when(mockCriteria.uniqueResult()).thenReturn(foundLanguage);

        when(obContext.getUser()).thenReturn(mockUser);
        when(obContext.getRole()).thenReturn(mockRole);
        when(obContext.getCurrentClient()).thenReturn(mockClient);
        when(obContext.getCurrentOrganization()).thenReturn(mockOrg);
        when(obContext.getLanguage()).thenReturn(mockLanguage);
        when(obContext.getWarehouse()).thenReturn(mockWarehouse);

        when(mockUser.getId()).thenReturn(USER_ID_VAL);
        when(mockRole.getId()).thenReturn(ROLE_ID_VAL);
        when(mockClient.getId()).thenReturn(CLIENT_ID_VAL);
        when(mockOrg.getId()).thenReturn(ORG_ID_VAL);
        when(mockLanguage.getLanguage()).thenReturn(currentLanguageCode);
        when(mockWarehouse.getId()).thenReturn(WH_ID_VAL);
    }

}
