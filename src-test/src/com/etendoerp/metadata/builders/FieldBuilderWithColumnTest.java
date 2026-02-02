package com.etendoerp.metadata.builders;

import static com.etendoerp.metadata.MetadataTestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.Property;
import org.openbravo.client.application.ApplicationConstants;
import org.openbravo.client.application.DynamicExpressionParser;
import org.openbravo.client.kernel.KernelUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.FieldAccess;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.ad.ui.Field;
import org.openbravo.model.ad.ui.Process;
import org.openbravo.model.ad.ui.Tab;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.service.json.DataResolvingMode;
import org.openbravo.service.json.DataToJsonConverter;

import java.lang.reflect.Method;
import java.util.Map;

import com.etendoerp.etendorx.utils.DataSourceUtils;
import com.etendoerp.metadata.utils.Constants;
import com.etendoerp.metadata.utils.LegacyUtils;
import com.etendoerp.metadata.utils.Utils;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class FieldBuilderWithColumnTest {

    @Mock
    private Field field;
    @Mock
    private FieldAccess fieldAccess;
    @Mock
    private Column column;
    @Mock
    private Reference reference;
    @Mock
    private Table table;
    @Mock
    private Tab tab;
    @Mock
    private Tab referencedTab;
    @Mock
    private Window referencedWindow;
    @Mock
    private Language language;
    @Mock
    private Process process;
    @Mock
    private Property referencedProperty;
    @Mock
    private Property fieldProperty;
    @Mock
    private Entity referencedEntity;
    @Mock
    private Entity tabEntity;
    @Mock
    private OBDal obDal;
    @Mock
    private OBCriteria<Tab> criteria;
    @Mock
    private OBContext obContext;
    private static final String BUTTON_REF_LIST_STRING = "buttonRefList";
    private static final String WINDOW_ID_STRING = "window-id";
    private static final String ROLE_ID_STRING = "role-id";

    private static final String METH_CHECK_ACCESS_IN_DB = "checkAccessInDB";
    private static final String METH_IS_WINDOW_ACCESSIBLE = "isWindowAccessible";
    private static final String METH_ADD_LINK_ACCESSIBILITY = "addLinkAccessibilityInfo";

    private static final String IS_REFERENCE_WINDOW_STRING = "isReferencedWindowAccessible";

    private FieldBuilderWithColumn fieldBuilder;

    @BeforeEach
    void setUp() {
        when(field.getId()).thenReturn(FIELD_ID);
        when(field.getColumn()).thenReturn(column);
        when(field.getTab()).thenReturn(tab);
        when(field.getName()).thenReturn(TEST_FIELD);
        when(field.isReadOnly()).thenReturn(false);

        when(column.getDBColumnName()).thenReturn(TEST_COLUMN_NAME);
        when(column.getTable()).thenReturn(table);
        when(column.getReference()).thenReturn(reference);
        when(column.getReferenceSearchKey()).thenReturn(reference);
        when(column.isUpdatable()).thenReturn(true);
        when(column.isMandatory()).thenReturn(false);

        when(reference.getId()).thenReturn("reference-id");

        when(tab.getTable()).thenReturn(table);
        when(table.getDBTableName()).thenReturn(TEST_TABLE_NAME);
        when(table.getDataOriginType()).thenReturn(ApplicationConstants.TABLEBASEDTABLE);

        when(fieldProperty.getEntity()).thenReturn(tabEntity);
        when(tabEntity.getTableId()).thenReturn(TABLE_ID);

        when(obContext.getLanguage()).thenReturn(language);

        // Clear static cache via reflection
        try {
            java.lang.reflect.Field cacheField = FieldBuilderWithColumn.class.getDeclaredField("windowAccessCache");
            cacheField.setAccessible(true);
            Map<?, ?> cache = (Map<?, ?>) cacheField.get(null);
            cache.clear();
        } catch (Exception e) {
            fail("Could not clear windowAccessCache: " + e.getMessage());
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Helpers */
    /* ---------------------------------------------------------------------- */

    private void setupWindowAccessMocks(String windowId, OBCriteria<WindowAccess> criteriaMock,
            WindowAccess windowAccess) {
        when(obDal.get(Window.class, windowId)).thenReturn(mock(Window.class));
        when(obDal.createCriteria(WindowAccess.class)).thenReturn(criteriaMock);
        when(criteriaMock.add(any())).thenReturn(criteriaMock);
        when(criteriaMock.setMaxResults(1)).thenReturn(criteriaMock);
        when(criteriaMock.uniqueResult()).thenReturn(windowAccess);
    }

    private Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private void setJson(FieldBuilder builder, JSONObject json) throws Exception {
        java.lang.reflect.Field jsonField = FieldBuilder.class.getDeclaredField("json");
        jsonField.setAccessible(true);
        jsonField.set(builder, json);
    }

    private JSONObject executeToJSON(Runnable extraMocks) throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(
                        DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                            when(mock.toJsonObject(any(Column.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id",
                                            COLUMN_ID));
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);
            mockedDataSourceUtils.when(
                    () -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
                    .thenReturn(new String[] { TEST_FIELD });

            when(obContext.getLanguage()).thenReturn(language);

            if (extraMocks != null) {
                extraMocks.run();
            }

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            return fieldBuilder.toJSON();
        }
    }

    private JSONObject executeWithExpressionParser(
            String jsExpression,
            Runnable extraMocks) throws JSONException {

        try (MockedConstruction<DynamicExpressionParser> ignored = mockConstruction(
                DynamicExpressionParser.class,
                (mock, context) -> when(mock.getJSExpression()).thenReturn(jsExpression))) {
            return executeToJSON(extraMocks);
        }
    }

    /* ---------------------------------------------------------------------- */
    /* Tests */
    /* ---------------------------------------------------------------------- */

    @Test
    void testToJSONWithProcessAction() throws JSONException {
        when(column.getProcess()).thenReturn(process);

        try (MockedStatic<ProcessActionBuilder> mocked = mockStatic(ProcessActionBuilder.class)) {

            mocked.when(() -> ProcessActionBuilder.getFieldProcess(field, process))
                    .thenReturn(new JSONObject().put("processId", "process-id"));

            JSONObject result = executeToJSON(null);
            assertTrue(result.has("processAction"));
        }
    }

    @Test
    void testToJSONWithLegacyProcess() throws JSONException {
        try (MockedStatic<LegacyUtils> legacy = mockStatic(LegacyUtils.class);
                MockedStatic<ProcessActionBuilder> builder = mockStatic(ProcessActionBuilder.class)) {

            legacy.when(() -> LegacyUtils.isLegacyProcess(FIELD_ID)).thenReturn(true);
            legacy.when(() -> LegacyUtils.getLegacyProcess(FIELD_ID)).thenReturn(process);

            builder.when(() -> ProcessActionBuilder.getFieldProcess(field, process))
                    .thenReturn(new JSONObject().put("processId", "legacy"));

            JSONObject result = executeToJSON(null);
            assertTrue(result.has("processAction"));
        }
    }

    @Test
    void testToJSONWithReadOnlyLogic() throws JSONException {
        when(column.getReadOnlyLogic()).thenReturn("@test@='Y'");

        JSONObject result = executeWithExpressionParser(
                "test == 'Y'",
                null);

        assertEquals("test == 'Y'", result.getString("readOnlyLogicExpression"));
    }

    @Test
    void testToJSONWithComplexDisplayLogic() throws JSONException {
        when(field.getDisplayLogic())
                .thenReturn("@Product@='Y' & @Customer@!='null'");

        JSONObject result = executeWithExpressionParser(
                "context.Product=='Y' && context.Customer!=null",
                null);

        assertNotNull(result);
    }

    @Test
    void testToJSONWithMandatoryColumn() throws JSONException {
        when(column.isMandatory()).thenReturn(true);

        JSONObject result = executeToJSON(null);
        assertNotNull(result);
    }

    @Test
    void testToJSONWithButtonReferenceList() throws JSONException {
        when(column.getReference().getId()).thenReturn(Constants.BUTTON_REFERENCE_ID);

        org.openbravo.model.ad.domain.List item = mock(org.openbravo.model.ad.domain.List.class);

        when(item.getId()).thenReturn(LIST_ID);
        when(item.getSearchKey()).thenReturn("BTN");
        when(item.get(anyString(), any(Language.class), anyString()))
                .thenReturn("Button");

        when(reference.getADListList()).thenReturn(List.of(item));

        JSONObject result = executeToJSON(null);

        assertTrue(result.has(BUTTON_REF_LIST_STRING));
        assertEquals(1, result.getJSONArray(BUTTON_REF_LIST_STRING).length());
    }

    @Test
    void testToJSONWithoutButtonReference() throws JSONException {
        when(column.getReference().getId()).thenReturn("OTHER");

        JSONObject result = executeToJSON(null);
        assertFalse(result.has(BUTTON_REF_LIST_STRING));
    }

    @Test
    void testToJSONWithEmptyReferenceList() throws JSONException {
        when(reference.getId()).thenReturn(Constants.LIST_REFERENCE_ID);
        when(reference.getADListList()).thenReturn(Collections.emptyList());

        JSONObject result = executeToJSON(null);

        assertTrue(result.has(REF_LIST));
        assertEquals(0, result.getJSONArray(REF_LIST).length());
    }

    @Test
    void testIsParentRecordPropertyTrue() throws JSONException {
        Tab parentTab = mock(Tab.class);
        Table parentTable = mock(Table.class);
        Entity parentEntity = mock(Entity.class);

        when(column.isLinkToParentColumn()).thenReturn(true);
        when(parentTab.getTable()).thenReturn(parentTable);
        when(parentTable.getDBTableName()).thenReturn(TEST_TABLE_NAME);
        when(parentTable.getDataOriginType()).thenReturn(ApplicationConstants.TABLEBASEDTABLE);

        when(referencedProperty.getEntity()).thenReturn(parentEntity);
        when(fieldProperty.getReferencedProperty()).thenReturn(referencedProperty);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedStatic<org.openbravo.base.model.ModelProvider> mockedModelProvider = mockStatic(
                        org.openbravo.base.model.ModelProvider.class);
                MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
                MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(
                        DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                            when(mock.toJsonObject(any(Column.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id",
                                            COLUMN_ID));
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            // Mock OBDal to avoid EntityAccessChecker null issue
            mockedOBDal.when(OBDal::getInstance).thenReturn(obDal);
            when(obDal.get(eq(Table.class), anyString())).thenReturn(table);
            when(obDal.createCriteria(Tab.class)).thenReturn(criteria);
            when(criteria.add(any(Criterion.class))).thenReturn(criteria);
            when(criteria.setMaxResults(anyInt())).thenReturn(criteria);
            when(criteria.uniqueResult()).thenReturn(null);

            // Mock Utils.getReferencedTab to avoid internal OBDal call
            mockedUtils.when(() -> Utils.getReferencedTab(any(Property.class))).thenReturn(null);

            KernelUtils kernelUtilsInstance = mock(KernelUtils.class);
            mockedKernelUtils.when(KernelUtils::getInstance).thenReturn(kernelUtilsInstance);
            when(kernelUtilsInstance.getParentTab(tab)).thenReturn(parentTab);
            mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);

            org.openbravo.base.model.ModelProvider modelProviderInstance = mock(
                    org.openbravo.base.model.ModelProvider.class);
            mockedModelProvider.when(org.openbravo.base.model.ModelProvider::getInstance)
                    .thenReturn(modelProviderInstance);
            when(modelProviderInstance.getEntityByTableName(TEST_TABLE_NAME)).thenReturn(parentEntity);

            mockedDataSourceUtils.when(
                    () -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
                    .thenReturn(new String[] { TEST_FIELD });

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            JSONObject result = fieldBuilder.toJSON();

            assertTrue(result.has("isParentRecordProperty"));
            assertTrue(result.getBoolean("isParentRecordProperty"));
        }
    }

    @Test
    void testReferencedPropertyExceptionIsHandled() throws JSONException {
        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(
                        DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                            when(mock.toJsonObject(any(Column.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(new JSONObject().put("id",
                                            COLUMN_ID));
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedKernelUtils.when(() -> KernelUtils.getProperty(field))
                    .thenThrow(new RuntimeException("Test exception"));

            mockedDataSourceUtils.when(
                    () -> DataSourceUtils.getHQLColumnName(true, TEST_TABLE_NAME, TEST_COLUMN_NAME))
                    .thenReturn(new String[] { TEST_FIELD });

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            JSONObject result = fieldBuilder.toJSON();

            // Should not throw, and should not have referencedEntity
            assertNotNull(result);
            assertFalse(result.has("referencedEntity"));
        }
    }

    @Test
    void testGetColumnUpdatableReturnsTrueWhenColumnIsNull() throws JSONException {
        when(field.getColumn()).thenReturn(null);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<KernelUtils> mockedKernelUtils = mockStatic(KernelUtils.class);
                MockedStatic<DataSourceUtils> mockedDataSourceUtils = mockStatic(DataSourceUtils.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(
                        DataToJsonConverter.class,
                        (mock, context) -> {
                            JSONObject base = new JSONObject().put("id", FIELD_ID);
                            when(mock.toJsonObject(any(Field.class),
                                    eq(DataResolvingMode.FULL_TRANSLATABLE)))
                                    .thenReturn(base);
                        })) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getLanguage()).thenReturn(language);

            mockedKernelUtils.when(() -> KernelUtils.getProperty(field)).thenReturn(fieldProperty);

            mockedDataSourceUtils.when(
                    () -> DataSourceUtils.getHQLColumnName(anyBoolean(), anyString(), anyString()))
                    .thenReturn(new String[] { TEST_FIELD });

            // FieldBuilderWithColumn cannot be instantiated with null column
            // This test verifies the getColumnUpdatable method returns true when column is
            // null
            // by checking that FieldBuilderWithColumn handles the null case in
            // getColumnUpdatable
            when(field.getColumn()).thenReturn(column); // reset for constructor
            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);

            // After construction, simulate null column
            when(field.getColumn()).thenReturn(null);
            // The getColumnUpdatable should return true as fallback
            assertTrue(fieldBuilder.getColumnUpdatable());
        }
    }

    @Test
    void testCheckAccessInDBAccessible() throws Exception {
        Role role = mock(Role.class);
        String windowId = WINDOW_ID_STRING;
        @SuppressWarnings("unchecked")
        OBCriteria<WindowAccess> criteriaMock = mock(OBCriteria.class);

        try (MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
                MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
            setupWindowAccessMocks(windowId, criteriaMock, mock(WindowAccess.class));

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            boolean result = (boolean) invokePrivate(fieldBuilder, METH_CHECK_ACCESS_IN_DB,
                    new Class[] { Role.class, String.class }, role, windowId);
            assertTrue(result);
        }
    }

    @Test
    void testCheckAccessInDBNotAccessible() throws Exception {
        Role role = mock(Role.class);
        String windowId = WINDOW_ID_STRING;
        @SuppressWarnings("unchecked")
        OBCriteria<WindowAccess> criteriaMock = mock(OBCriteria.class);

        try (MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
                MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
            setupWindowAccessMocks(windowId, criteriaMock, null);

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            boolean result = (boolean) invokePrivate(fieldBuilder, METH_CHECK_ACCESS_IN_DB,
                    new Class[] { Role.class, String.class }, role, windowId);
            assertFalse(result);
        }
    }

    @Test
    void testIsWindowAccessibleCached() throws Exception {
        Role role = mock(Role.class);
        when(role.getId()).thenReturn(ROLE_ID_STRING);
        String windowId = WINDOW_ID_STRING;
        @SuppressWarnings("unchecked")
        OBCriteria<WindowAccess> criteriaMock = mock(OBCriteria.class);

        try (MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
                MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {
            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getRole()).thenReturn(role);
            mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(obDal);

            setupWindowAccessMocks(windowId, criteriaMock, mock(WindowAccess.class));

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);

            // First call hits DB
            invokePrivate(fieldBuilder, METH_IS_WINDOW_ACCESSIBLE, new Class[] { String.class }, windowId);
            // Second call should use cache
            invokePrivate(fieldBuilder, METH_IS_WINDOW_ACCESSIBLE, new Class[] { String.class }, windowId);

            verify(obDal, times(1)).createCriteria(WindowAccess.class);
        }
    }

    @Test
    void testAddLinkAccessibilityInfoWhenAccessible() throws JSONException {
        // Prepare JSON with referencedWindowId
        JSONObject json = new JSONObject();
        json.put(PROP_REFERENCED_WINDOW_ID, WINDOW_ID_STRING);

        Role role = mock(Role.class);
        when(role.getId()).thenReturn(ROLE_ID_STRING);
        @SuppressWarnings("unchecked")
        OBCriteria<WindowAccess> criteriaMock = mock(OBCriteria.class);

        try (MockedStatic<OBContext> mockedOBContext = mockStatic(OBContext.class);
                MockedStatic<OBDal> mockedOBDal = mockStatic(OBDal.class);
                MockedConstruction<DataToJsonConverter> ignored = mockConstruction(DataToJsonConverter.class)) {

            mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
            when(obContext.getRole()).thenReturn(role);
            mockedOBDal.when(OBDal::getReadOnlyInstance).thenReturn(obDal);

            setupWindowAccessMocks(WINDOW_ID_STRING, criteriaMock, mock(WindowAccess.class));

            fieldBuilder = new FieldBuilderWithColumn(field, fieldAccess);
            try {
                setJson(fieldBuilder, json);
                invokePrivate(fieldBuilder, METH_ADD_LINK_ACCESSIBILITY, new Class[] {});

                assertTrue(json.has(IS_REFERENCE_WINDOW_STRING));
                assertTrue(json.getBoolean(IS_REFERENCE_WINDOW_STRING));
            } catch (Exception e) {
                fail("Reflection failed: " + e.getMessage());
            }
        }
    }
}
